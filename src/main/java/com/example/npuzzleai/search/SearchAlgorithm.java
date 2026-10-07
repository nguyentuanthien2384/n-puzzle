package com.example.npuzzleai.search;

import com.example.npuzzleai.core.PuzzleProblem;

/**
 * Hợp đồng chung cho mọi thuật toán tìm kiếm.
 *
 * <p>GUI, CLI, benchmark và unit test đều gọi cùng một implementation qua interface này,
 * tránh tình trạng benchmark đo một code path khác với giao diện. Implementation phải
 * thread-safe theo nghĩa: mọi trạng thái của một lần giải nằm trong biến cục bộ, để một
 * instance có thể được gọi song song trong benchmark.</p>
 *
 * <p>Plugin bên ngoài có thể cài đặt interface này và khai báo trong
 * {@code META-INF/services/com.example.npuzzleai.search.SearchAlgorithm}; xem
 * {@code AlgorithmRegistry.loadPlugins}.</p>
 */
public interface SearchAlgorithm {
    /** Mã định danh duy nhất, có thể kèm tham số, ví dụ "astar", "wastar:2.0", "sma:50000". */
    String id();

    String displayName();

    default String description() {
        return "";
    }

    AlgorithmProperties properties();

    default boolean supports(int size) {
        return size <= properties().maxRecommendedSize();
    }

    SearchResult solve(PuzzleProblem problem, Heuristic heuristic, SearchBudget budget, SearchObserver observer);

    default SearchResult solve(PuzzleProblem problem, Heuristic heuristic) {
        return solve(problem, heuristic, SearchBudget.DEFAULT, SearchObserver.NONE);
    }
}
