package com.example.npuzzleai.learning;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.heuristics.BasicHeuristics;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.search.Heuristic;

/**
 * Đặc trưng đầu vào cho learned heuristic - đều tính tương đối theo {@link Goal} nên mô hình
 * dùng được cho đích tuỳ ý cùng kích thước.
 *
 * <p>Bộ BASIC chỉ gồm đặc trưng rẻ (không có PDB) để việc học có ý nghĩa trên 3x3, nơi PDB đầy đủ
 * đã bằng h*. Bộ BASIC_PDB thêm tổng additive PDB (cho 4x4 trở lên) làm đặc trưng mạnh.</p>
 */
public final class FeatureExtractor {
    public enum FeatureSet { BASIC, BASIC_PDB }

    static final String[] BASIC_NAMES = {
            "manhattan", "linearConflict", "misplaced", "wrongRow", "wrongCol",
            "walkingDistance", "blankDistance", "cornerConflicts", "manhattanSquaredPerCell"
    };

    private final FeatureSet set;
    private final Heuristic walkingDistance;
    private final Heuristic pdb;

    public FeatureExtractor(FeatureSet set) {
        this.set = set;
        this.walkingDistance = HeuristicRegistry.defaults().create("walking-distance");
        this.pdb = set == FeatureSet.BASIC_PDB ? HeuristicRegistry.defaults().create("apdb") : null;
    }

    public FeatureSet featureSet() {
        return set;
    }

    public int dimension() {
        return BASIC_NAMES.length + (set == FeatureSet.BASIC_PDB ? 1 : 0);
    }

    /** Chuẩn bị bảng phụ (Walking Distance, PDB) cho đích. */
    public void prepare(Goal goal) {
        if (walkingDistance.supports(goal.size())) walkingDistance.prepare(goal);
        if (pdb != null && pdb.supports(goal.size())) pdb.prepare(goal);
    }

    public double[] extract(Board board, Goal goal) {
        int size = board.size();
        int n = board.cellCount();
        double[] f = new double[dimension()];
        int md = BasicHeuristics.manhattan(board, goal);
        int misplaced = 0, wrongRow = 0, wrongCol = 0;
        for (int i = 0; i < n; i++) {
            int t = board.tileAt(i);
            if (t == 0) continue;
            if (goal.positionOf(t) != i) misplaced++;
            if (goal.rowOf(t) != i / size) wrongRow++;
            if (goal.colOf(t) != i % size) wrongCol++;
        }
        int b = board.blankIndex();
        int gb = goal.blankPosition();
        f[0] = md;
        f[1] = BasicHeuristics.linearConflictPenalty(board, goal);
        f[2] = misplaced;
        f[3] = wrongRow;
        f[4] = wrongCol;
        f[5] = walkingDistance.supports(size) ? walkingDistance.estimate(board, goal) : md;
        f[6] = Math.abs(b / size - gb / size) + Math.abs(b % size - gb % size);
        f[7] = cornerConflicts(board, goal);
        f[8] = (double) md * md / n;
        if (pdb != null) f[9] = pdb.supports(size) ? pdb.estimate(board, goal) : md;
        return f;
    }

    /**
     * Số góc của đích (không chứa ô trống) mà ô góc sai vị trí trong khi hai ô kề góc đều đúng vị trí:
     * ô góc buộc phải "đẩy" một ô đúng ra ngoài - dấu hiệu chi phí ẩn mà Manhattan bỏ qua.
     */
    static int cornerConflicts(Board board, Goal goal) {
        int size = board.size();
        int last = size - 1;
        int[][] corners = {{0, 0, 0, 1, 1, 0}, {0, last, 0, last - 1, 1, last},
                {last, 0, last - 1, 0, last, 1}, {last, last, last - 1, last, last, last - 1}};
        Board g = goal.board();
        int count = 0;
        for (int[] c : corners) {
            int corner = c[0] * size + c[1];
            int a = c[2] * size + c[3];
            int d = c[4] * size + c[5];
            int goalTile = g.tileAt(corner);
            if (goalTile == 0 || board.tileAt(corner) == goalTile) continue;
            boolean aOk = g.tileAt(a) != 0 && board.tileAt(a) == g.tileAt(a);
            boolean dOk = g.tileAt(d) != 0 && board.tileAt(d) == g.tileAt(d);
            if (aOk && dOk) count++;
        }
        return count;
    }
}
