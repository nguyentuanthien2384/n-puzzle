package com.example.npuzzleai.search;

/**
 * Giới hạn tài nguyên cho một lần tìm kiếm. Giá trị 0 nghĩa là không giới hạn.
 *
 * <p>{@code maxMemoryBytes} là ngân sách bộ nhớ <i>ước lượng</i> theo số node đang giữ
 * nhân kích thước trung bình mỗi node ({@link SearchContext#estimateBytesPerNode}); SMA*
 * dùng nó để suy ra số node tối đa, A* và BFS dừng với trạng thái MEMORY_LIMIT khi vượt.</p>
 */
public record SearchBudget(long timeoutMillis, long maxExpandedNodes, long maxMemoryBytes) {
    public static final SearchBudget DEFAULT = new SearchBudget(60_000L, 0L, 0L);

    public SearchBudget {
        if (timeoutMillis < 0 || maxExpandedNodes < 0 || maxMemoryBytes < 0) {
            throw new IllegalArgumentException("Giới hạn không được âm");
        }
    }

    public static SearchBudget unlimited() {
        return new SearchBudget(0L, 0L, 0L);
    }

    public static SearchBudget ofTimeout(long timeoutMillis) {
        return new SearchBudget(timeoutMillis, 0L, 0L);
    }

    public SearchBudget withTimeout(long millis) {
        return new SearchBudget(millis, maxExpandedNodes, maxMemoryBytes);
    }

    public SearchBudget withMaxExpanded(long nodes) {
        return new SearchBudget(timeoutMillis, nodes, maxMemoryBytes);
    }

    public SearchBudget withMaxMemoryBytes(long bytes) {
        return new SearchBudget(timeoutMillis, maxExpandedNodes, bytes);
    }
}
