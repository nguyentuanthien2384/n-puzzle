package com.example.npuzzleai.search;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;

/**
 * Hàm đánh giá h(s) - ước lượng số bước còn lại tới đích.
 *
 * <p>Mỗi heuristic tự khai báo {@link HeuristicProperties}; bộ kiểm định
 * ({@code HeuristicVerifier}) và test suite xác minh các khai báo đó trên toàn bộ
 * không gian 3x3 bằng khoảng cách chính xác h*.</p>
 *
 * <p>Implementation phải thread-safe: benchmark có thể gọi {@link #estimate} từ nhiều luồng.</p>
 */
public interface Heuristic {
    String id();

    default String displayName() {
        return id();
    }

    int estimate(Board board, Goal goal);

    default HeuristicProperties properties() {
        return HeuristicProperties.unknown();
    }

    default boolean supports(int size) {
        return true;
    }

    /** Tiền xử lý cho một đích (dựng/nạp Pattern Database...). Gọi trước khi đo thời gian tìm kiếm. */
    default void prepare(Goal goal) {
    }
}
