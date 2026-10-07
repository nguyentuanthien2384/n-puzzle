package com.example.npuzzleai.heuristics;

import com.example.npuzzleai.WalkingDistance;
import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.HeuristicProperties;

/**
 * H7 - Walking Distance (Takahashi) trên API mới, dùng chung bảng tra với lớp {@link WalkingDistance}.
 * Bảng phụ thuộc hàng/cột đích của ô trống nên đúng với mọi đích. Chỉ hỗ trợ tới 4x4
 * (bảng 5x5 có hàng triệu trạng thái).
 */
public final class WalkingDistanceHeuristic implements Heuristic {
    private static final HeuristicProperties PROPERTIES = HeuristicProperties
            .admissibleAndConsistent("BFS trên không gian ma trận đếm hàng/cột; trội Manhattan")
            .withPreprocessing();

    @Override
    public String id() {
        return "walking-distance";
    }

    @Override
    public String displayName() {
        return "H7 Walking Distance";
    }

    @Override
    public boolean supports(int size) {
        return size >= 3 && size <= 4;
    }

    @Override
    public void prepare(Goal goal) {
        int size = goal.size();
        WalkingDistance.tableFor(size, goal.blankPosition() / size);
        WalkingDistance.tableFor(size, goal.blankPosition() % size);
    }

    @Override
    public int estimate(Board board, Goal goal) {
        return walkingDistance(board, goal);
    }

    static int walkingDistance(Board board, Goal goal) {
        int size = board.size();
        int n = board.cellCount();
        byte[][] vertical = new byte[size][size];
        byte[][] horizontal = new byte[size][size];
        for (int i = 0; i < n; i++) {
            int t = board.tileAt(i);
            if (t == 0) continue;
            vertical[i / size][goal.rowOf(t)]++;
            horizontal[i % size][goal.colOf(t)]++;
        }
        int blankGoal = goal.blankPosition();
        int vd = WalkingDistance.tableFor(size, blankGoal / size)
                .get(WalkingDistance.pack(vertical, board.blankRow(), size));
        int hd = WalkingDistance.tableFor(size, blankGoal % size)
                .get(WalkingDistance.pack(horizontal, board.blankCol(), size));
        return Math.max(0, vd) + Math.max(0, hd);
    }

    @Override
    public HeuristicProperties properties() {
        return PROPERTIES;
    }
}
