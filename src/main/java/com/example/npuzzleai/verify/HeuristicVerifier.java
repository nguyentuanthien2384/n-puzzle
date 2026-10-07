package com.example.npuzzleai.verify;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Move;
import com.example.npuzzleai.core.PermutationRank;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.HeuristicProperties;

/**
 * Kiểm định heuristic vét cạn trên toàn bộ thành phần tới được của 2x2/3x3.
 *
 * <p>Biến "chúng tôi tin heuristic này admissible" thành "đã kiểm tra trên toàn bộ 181.440 trạng thái
 * 3x3 và mọi cạnh giữa chúng". Lớp đánh giá nội tại (không chạy A*): so sánh trực tiếp h với h*,
 * đo độ chính xác, phát hiện vi phạm admissibility/consistency kèm phản ví dụ.</p>
 */
public final class HeuristicVerifier {
    private static final int ERROR_OFFSET = 128;

    private HeuristicVerifier() {
    }

    public static HeuristicReport verify(Heuristic heuristic, Goal goal) {
        return verify(heuristic, ExactDistanceTable.forGoal(goal));
    }

    public static HeuristicReport verify(Heuristic heuristic, ExactDistanceTable table) {
        long t0 = System.currentTimeMillis();
        Goal goal = table.goal();
        heuristic.prepare(goal);
        int space = table.permutationCount();
        int[] hv = new int[space];
        Board[] boards = new Board[space];

        // Lượt 1: tính h cho mọi trạng thái tới được.
        for (int r = 0; r < space; r++) {
            if (table.distanceAt(r) < 0) {
                hv[r] = -1;
                continue;
            }
            boards[r] = table.boardAt(r);
            hv[r] = heuristic.estimate(boards[r], goal);
        }

        int maxD = table.maxDistance();
        double[] sumHByD = new double[maxD + 1];
        long[] countByD = new long[maxD + 1];
        long[] errorCounts = new long[2 * ERROR_OFFSET];
        long violations = 0, consistency = 0, edges = 0, exact = 0;
        int maxOver = 0;
        String counter = "";
        int counterH = -1, counterHStar = -1;
        double sumH = 0, sumHStar = 0, sumError = 0;
        int states = 0;

        // Lượt 2: thống kê + kiểm tra mọi cạnh.
        for (int r = 0; r < space; r++) {
            int d = table.distanceAt(r);
            if (d < 0) continue;
            states++;
            int h = hv[r];
            sumH += h;
            sumHStar += d;
            sumError += d - h;
            sumHByD[d] += h;
            countByD[d]++;
            int errIndex = Math.max(0, Math.min(errorCounts.length - 1, d - h + ERROR_OFFSET));
            errorCounts[errIndex]++;
            if (h == d) exact++;
            if (h > d) {
                violations++;
                if (h - d > maxOver) {
                    maxOver = h - d;
                    counter = boards[r].toString();
                    counterH = h;
                    counterHStar = d;
                }
            }
            Board b = boards[r];
            for (Move m : b.legalMoves()) {
                int nr = PermutationRank.rank(b.move(m));
                edges++;
                if (h > 1 + hv[nr]) consistency++;
            }
        }

        double[] meanByD = new double[maxD + 1];
        for (int d = 0; d <= maxD; d++) meanByD[d] = countByD[d] == 0 ? 0 : sumHByD[d] / countByD[d];

        HeuristicProperties props = heuristic.properties();
        return new HeuristicReport(heuristic.id(), heuristic.displayName(), goal.toString(), states, edges,
                violations, maxOver, counter, counterH, counterHStar, consistency,
                heuristic.estimate(goal.board(), goal),
                sumH / states, sumHStar / states, sumError / states,
                median(errorCounts, states) - ERROR_OFFSET, (double) exact / states, meanByD,
                props.claimedAdmissible(), props.claimedConsistent(), props.experimental(),
                System.currentTimeMillis() - t0);
    }

    /**
     * Mức độ trội giữa hai heuristic: tỉ lệ trạng thái mà hA &gt; hB, = hB, &lt; hB.
     * "A trội B" khi tỉ lệ nhỏ hơn bằng 0.
     */
    public static double[] dominance(Heuristic a, Heuristic b, Goal goal) {
        ExactDistanceTable table = ExactDistanceTable.forGoal(goal);
        a.prepare(goal);
        b.prepare(goal);
        long greater = 0, equal = 0, less = 0;
        for (int r = 0; r < table.permutationCount(); r++) {
            if (table.distanceAt(r) < 0) continue;
            Board board = table.boardAt(r);
            int ha = a.estimate(board, goal);
            int hb = b.estimate(board, goal);
            if (ha > hb) greater++;
            else if (ha == hb) equal++;
            else less++;
        }
        double total = greater + equal + less;
        return new double[]{greater / total, equal / total, less / total};
    }

    private static double median(long[] counts, int total) {
        long half = (total + 1) / 2;
        long acc = 0;
        for (int i = 0; i < counts.length; i++) {
            acc += counts[i];
            if (acc >= half) return i;
        }
        return 0;
    }
}
