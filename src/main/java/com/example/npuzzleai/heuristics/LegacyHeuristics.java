package com.example.npuzzleai.heuristics;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.HeuristicProperties;

/**
 * H5/H6 của báo cáo gốc, chuyển nguyên logic sang API mới để đối chiếu.
 *
 * <p>Hai heuristic này được gắn nhãn <b>thử nghiệm</b> (claimedAdmissible = false):
 * H5 đếm linear conflict bằng cách quét running-max và cộng 2 cho mỗi ô nhỏ hơn max - có thể
 * đếm dư khi các xung đột chồng nhau (ví dụ thứ tự đích [2, 0, 1] cho +4 trong khi chỉ cần nhấc
 * một ô, +2). H6 cộng thêm "ô bị chặn" vào H5 mà chưa chứng minh phần cộng thêm không bị H5 tính trước.
 * Bộ kiểm định vét cạn 3x3 ({@code HeuristicVerifier}) sẽ cho biết thực tế chúng có vi phạm hay không.</p>
 */
public final class LegacyHeuristics {
    private LegacyHeuristics() {
    }

    /** H5 - Manhattan + xung đột tuyến tính quét running-max (bản báo cáo gốc). */
    public static final class H5 implements Heuristic {
        @Override
        public String id() {
            return "legacy-h5";
        }

        @Override
        public String displayName() {
            return "H5 MD + LC (running-max, gốc)";
        }

        @Override
        public int estimate(Board board, Goal goal) {
            return h5(board, goal);
        }

        @Override
        public HeuristicProperties properties() {
            return HeuristicProperties.experimental("Có thể đếm dư xung đột chồng nhau - cần kiểm định");
        }
    }

    /** H6 - H5 + số ô bị các ô đúng vị trí bao quanh vị trí đích (bản báo cáo gốc). */
    public static final class H6 implements Heuristic {
        @Override
        public String id() {
            return "legacy-h6";
        }

        @Override
        public String displayName() {
            return "H6 H5 + ô bị chặn (gốc)";
        }

        @Override
        public int estimate(Board board, Goal goal) {
            int size = board.size();
            int n = board.cellCount();
            Board g = goal.board();
            int distance = h5(board, goal);
            for (int i = 0; i < n; i++) {
                int tile = board.tileAt(i);
                if (tile == 0) continue;
                int gi = goal.positionOf(tile);
                if (i == gi) continue;
                int block = 0;
                int count = 0;
                if (gi / size != 0) {
                    count++;
                    if (board.tileAt(gi - size) == g.tileAt(gi - size)) block++;
                }
                if (gi / size != size - 1) {
                    count++;
                    if (board.tileAt(gi + size) == g.tileAt(gi + size)) block++;
                }
                if (gi % size != 0) {
                    count++;
                    if (board.tileAt(gi - 1) == g.tileAt(gi - 1)) block++;
                }
                if (gi % size != size - 1) {
                    count++;
                    if (board.tileAt(gi + 1) == g.tileAt(gi + 1)) block++;
                }
                if (count >= 2 && count == block) distance++;
            }
            return distance;
        }

        @Override
        public HeuristicProperties properties() {
            return HeuristicProperties.experimental("Cộng thêm vào H5 chưa được chứng minh - cần kiểm định");
        }
    }

    static int h5(Board board, Goal goal) {
        int size = board.size();
        int distance = BasicHeuristics.manhattan(board, goal);
        for (int row = 0; row < size; row++) {
            int maxGoalCol = -1;
            for (int col = 0; col < size; col++) {
                int tile = board.tileAt(row, col);
                if (tile == 0 || goal.rowOf(tile) != row) continue;
                int goalCol = goal.colOf(tile);
                if (goalCol > maxGoalCol) maxGoalCol = goalCol;
                else distance += 2;
            }
        }
        for (int col = 0; col < size; col++) {
            int maxGoalRow = -1;
            for (int row = 0; row < size; row++) {
                int tile = board.tileAt(row, col);
                if (tile == 0 || goal.colOf(tile) != col) continue;
                int goalRow = goal.rowOf(tile);
                if (goalRow > maxGoalRow) maxGoalRow = goalRow;
                else distance += 2;
            }
        }
        return distance;
    }
}
