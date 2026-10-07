package com.example.npuzzleai.search;

import java.util.function.BooleanSupplier;

/**
 * Cầu nối giữa thuật toán và visualizer/progress UI.
 *
 * <p>Không lưu mọi sự kiện của một lần tìm hàng triệu node: {@link #traceMode()} quyết định
 * mức chi tiết. Ở chế độ OFF thuật toán không tạo đối tượng {@link SearchEvent} nào nên
 * benchmark không bị visualization làm sai lệch thời gian. {@link #onProgress} vẫn được gọi
 * định kỳ (mỗi vài nghìn node) để cập nhật thanh tiến độ.</p>
 *
 * <p>Các callback chạy trên luồng tìm kiếm; UI phải tự chuyển sang JavaFX thread.</p>
 */
public interface SearchObserver {
    SearchObserver NONE = new SearchObserver() {
    };

    default TraceMode traceMode() {
        return TraceMode.OFF;
    }

    /** Với SAMPLED: phát sự kiện expand mỗi {@code sampleInterval} node. */
    default int sampleInterval() {
        return 1024;
    }

    /** Thuật toán kiểm tra định kỳ để dừng sớm (nút Dừng, Task.cancel()). */
    default boolean isCancelled() {
        return false;
    }

    default void onExpand(SearchEvent event) {
    }

    default void onGenerate(SearchEvent event) {
    }

    default void onPrune(SearchEvent event) {
    }

    default void onGoal(SearchEvent event) {
    }

    /** Ảnh chụp tiến độ; đối tượng metrics còn đang được ghi - phải {@code copy()} nếu giữ lại. */
    default void onProgress(SearchMetrics liveMetrics) {
    }

    static SearchObserver cancellable(BooleanSupplier cancelled) {
        return new SearchObserver() {
            @Override
            public boolean isCancelled() {
                return cancelled.getAsBoolean();
            }
        };
    }
}
