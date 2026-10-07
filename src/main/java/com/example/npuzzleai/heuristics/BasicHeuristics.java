package com.example.npuzzleai.heuristics;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.HeuristicProperties;

/**
 * Các heuristic đơn giản tính trực tiếp từ vị trí ô (H0-H4 và Manhattan + Linear Conflict chuẩn).
 * Tất cả nhận {@link Goal} nên đúng với đích tuỳ ý.
 */
public final class BasicHeuristics {
    private BasicHeuristics() {
    }

    /** h = 0: A* trở thành tìm kiếm chi phí đều (uniform-cost) - baseline không có thông tin. */
    public static final class Zero implements Heuristic {
        @Override
        public String id() {
            return "zero";
        }

        @Override
        public String displayName() {
            return "h = 0 (không thông tin)";
        }

        @Override
        public int estimate(Board board, Goal goal) {
            return 0;
        }

        @Override
        public HeuristicProperties properties() {
            return HeuristicProperties.admissibleAndConsistent("Baseline: A* thành uniform-cost search");
        }
    }

    /** H1 - số ô sai vị trí. */
    public static final class Misplaced implements Heuristic {
        @Override
        public String id() {
            return "misplaced";
        }

        @Override
        public String displayName() {
            return "H1 Số ô sai vị trí";
        }

        @Override
        public int estimate(Board board, Goal goal) {
            int n = board.cellCount();
            int d = 0;
            for (int i = 0; i < n; i++) {
                int t = board.tileAt(i);
                if (t != 0 && goal.positionOf(t) != i) d++;
            }
            return d;
        }

        @Override
        public HeuristicProperties properties() {
            return HeuristicProperties.admissibleAndConsistent("Mỗi ô sai vị trí cần ít nhất 1 nước");
        }
    }

    /** H2 - tổng khoảng cách Manhattan. */
    public static final class Manhattan implements Heuristic {
        @Override
        public String id() {
            return "manhattan";
        }

        @Override
        public String displayName() {
            return "H2 Manhattan";
        }

        @Override
        public int estimate(Board board, Goal goal) {
            return manhattan(board, goal);
        }

        @Override
        public HeuristicProperties properties() {
            return HeuristicProperties.admissibleAndConsistent("Mỗi nước đi giảm tối đa 1 đơn vị Manhattan");
        }
    }

    /** H3 - tổng phần nguyên khoảng cách Euclid (luôn &lt;= Manhattan). */
    public static final class Euclidean implements Heuristic {
        @Override
        public String id() {
            return "euclid";
        }

        @Override
        public String displayName() {
            return "H3 Euclid";
        }

        @Override
        public int estimate(Board board, Goal goal) {
            int size = board.size();
            int n = board.cellCount();
            int d = 0;
            for (int i = 0; i < n; i++) {
                int t = board.tileAt(i);
                if (t == 0) continue;
                int dr = i / size - goal.rowOf(t);
                int dc = i % size - goal.colOf(t);
                d += (int) Math.sqrt(dr * dr + dc * dc);
            }
            return d;
        }

        @Override
        public HeuristicProperties properties() {
            return HeuristicProperties.admissibleAndConsistent("floor(Euclid) <= Manhattan, mỗi nước đổi <= 1");
        }
    }

    /** H4 - số ô sai hàng + số ô sai cột. */
    public static final class RowColumn implements Heuristic {
        @Override
        public String id() {
            return "rowcol";
        }

        @Override
        public String displayName() {
            return "H4 Sai hàng + sai cột";
        }

        @Override
        public int estimate(Board board, Goal goal) {
            int size = board.size();
            int n = board.cellCount();
            int d = 0;
            for (int i = 0; i < n; i++) {
                int t = board.tileAt(i);
                if (t == 0) continue;
                if (goal.rowOf(t) != i / size) d++;
                if (goal.colOf(t) != i % size) d++;
            }
            return d;
        }

        @Override
        public HeuristicProperties properties() {
            return HeuristicProperties.admissibleAndConsistent("Mỗi nước chỉ đổi hàng hoặc cột của một ô");
        }
    }

    /**
     * Manhattan + Linear Conflict chuẩn (Hansson-Mayer-Yung 1992).
     *
     * <p>Trên mỗi hàng/cột, xét các ô đang nằm đúng hàng/cột đích của nó; số ô tối thiểu phải nhấc
     * ra khỏi đường để hết xung đột = k - LIS (dãy con tăng dài nhất theo vị trí đích). Mỗi ô nhấc ra
     * tốn thêm ít nhất 2 nước. Khác bản "cứ mỗi cặp xung đột +2" (dễ đếm trùng), cách đếm LIS
     * không bao giờ đếm dư.</p>
     */
    public static final class LinearConflict implements Heuristic {
        @Override
        public String id() {
            return "linear-conflict";
        }

        @Override
        public String displayName() {
            return "Manhattan + Linear Conflict (LIS)";
        }

        @Override
        public int estimate(Board board, Goal goal) {
            return manhattan(board, goal) + linearConflictPenalty(board, goal);
        }

        @Override
        public HeuristicProperties properties() {
            return HeuristicProperties.admissibleAndConsistent("LC chuẩn đếm bằng LIS - không đếm trùng");
        }
    }

    public static int manhattan(Board board, Goal goal) {
        int size = board.size();
        int n = board.cellCount();
        int d = 0;
        for (int i = 0; i < n; i++) {
            int t = board.tileAt(i);
            if (t == 0) continue;
            d += Math.abs(i / size - goal.rowOf(t)) + Math.abs(i % size - goal.colOf(t));
        }
        return d;
    }

    /** Phần cộng thêm của Linear Conflict chuẩn: 2 * tổng (k - LIS) trên mọi hàng và cột. */
    public static int linearConflictPenalty(Board board, Goal goal) {
        int size = board.size();
        int[] seq = new int[size];
        int[] tails = new int[size];
        int conflicts = 0;
        for (int row = 0; row < size; row++) {
            int k = 0;
            for (int col = 0; col < size; col++) {
                int t = board.tileAt(row, col);
                if (t != 0 && goal.rowOf(t) == row) seq[k++] = goal.colOf(t);
            }
            conflicts += k - lis(seq, k, tails);
        }
        for (int col = 0; col < size; col++) {
            int k = 0;
            for (int row = 0; row < size; row++) {
                int t = board.tileAt(row, col);
                if (t != 0 && goal.colOf(t) == col) seq[k++] = goal.rowOf(t);
            }
            conflicts += k - lis(seq, k, tails);
        }
        return 2 * conflicts;
    }

    /**
     * Mặt nạ các ô tham gia xung đột tuyến tính (có ít nhất một ô khác cùng hàng/cột đích nằm ngược thứ tự).
     * Dùng cho chế độ Teaching của giao diện.
     */
    public static boolean[] conflictMask(Board board, Goal goal) {
        int size = board.size();
        boolean[] mask = new boolean[board.cellCount()];
        for (int line = 0; line < size; line++) {
            for (int a = 0; a < size; a++) {
                for (int b = a + 1; b < size; b++) {
                    int ia = line * size + a, ib = line * size + b;
                    int ta = board.tileAt(ia), tb = board.tileAt(ib);
                    if (ta != 0 && tb != 0 && goal.rowOf(ta) == line && goal.rowOf(tb) == line
                            && goal.colOf(ta) > goal.colOf(tb)) {
                        mask[ia] = mask[ib] = true;
                    }
                    int ja = a * size + line, jb = b * size + line;
                    int ua = board.tileAt(ja), ub = board.tileAt(jb);
                    if (ua != 0 && ub != 0 && goal.colOf(ua) == line && goal.colOf(ub) == line
                            && goal.rowOf(ua) > goal.rowOf(ub)) {
                        mask[ja] = mask[jb] = true;
                    }
                }
            }
        }
        return mask;
    }

    /** Độ dài dãy con tăng ngặt dài nhất, O(k log k). */
    private static int lis(int[] seq, int k, int[] tails) {
        int len = 0;
        for (int i = 0; i < k; i++) {
            int lo = 0, hi = len;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (tails[mid] < seq[i]) lo = mid + 1;
                else hi = mid;
            }
            tails[lo] = seq[i];
            if (lo == len) len++;
        }
        return len;
    }
}
