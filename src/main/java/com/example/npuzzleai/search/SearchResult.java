package com.example.npuzzleai.search;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Move;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả chuẩn hoá của một lần tìm kiếm.
 *
 * @param algorithmId mã thuật toán (kèm tham số)
 * @param heuristicId mã heuristic, "none" nếu không dùng
 * @param status      trạng thái kết thúc
 * @param start       trạng thái xuất phát
 * @param goal        trạng thái đích
 * @param moves       chuỗi nước đi (rỗng nếu không giải được)
 * @param metrics     bộ đếm instrumentation
 * @param message     mô tả cho người dùng
 */
public record SearchResult(String algorithmId,
                           String heuristicId,
                           SearchStatus status,
                           Board start,
                           Goal goal,
                           List<Move> moves,
                           SearchMetrics metrics,
                           String message) {

    public SearchResult {
        moves = List.copyOf(moves);
    }

    public boolean solved() {
        return status == SearchStatus.SOLVED;
    }

    public int length() {
        return solved() ? moves.size() : -1;
    }

    /** Danh sách trạng thái từ start tới goal (gồm cả hai đầu). */
    public List<Board> path() {
        List<Board> path = new ArrayList<>(moves.size() + 1);
        Board b = start;
        path.add(b);
        for (Move m : moves) {
            b = b.move(m);
            path.add(b);
        }
        return path;
    }

    /** Kiểm tra độc lập: áp dụng chuỗi nước đi từ start có tới đúng đích hay không. */
    public boolean pathReachesGoal() {
        if (!solved()) return false;
        try {
            return goal.isGoal(start.apply(moves));
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
