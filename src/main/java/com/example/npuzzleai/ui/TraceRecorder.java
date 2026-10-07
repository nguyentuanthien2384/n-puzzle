package com.example.npuzzleai.ui;

import com.example.npuzzleai.search.SearchEvent;
import com.example.npuzzleai.search.SearchMetrics;
import com.example.npuzzleai.search.SearchObserver;
import com.example.npuzzleai.search.TraceMode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Observer của Search Lab: ghi sự kiện expand (có giới hạn) cho Search Replay, đếm heatmap vị trí
 * ô trống và giữ ảnh chụp tiến độ mới nhất để UI đọc định kỳ (thay vì Platform.runLater mỗi node).
 *
 * <p>Danh sách sự kiện chỉ được ghi bởi luồng tìm kiếm và đọc sau khi Task hoàn tất.</p>
 */
final class TraceRecorder implements SearchObserver {
    private final TraceMode mode;
    private final int interval;
    private final int maxEvents;
    private final BooleanSupplier cancelled;
    private final List<SearchEvent> events = new ArrayList<>();
    private final long[] blankHeat;
    private volatile SearchMetrics latestMetrics;
    private volatile SearchEvent latestEvent;
    private volatile boolean truncated;

    TraceRecorder(int cells, TraceMode mode, int interval, int maxEvents, BooleanSupplier cancelled) {
        this.mode = mode;
        this.interval = Math.max(1, interval);
        this.maxEvents = maxEvents;
        this.cancelled = cancelled;
        this.blankHeat = new long[cells];
    }

    @Override
    public TraceMode traceMode() {
        return mode;
    }

    @Override
    public int sampleInterval() {
        return interval;
    }

    @Override
    public boolean isCancelled() {
        return cancelled.getAsBoolean();
    }

    @Override
    public void onExpand(SearchEvent event) {
        latestEvent = event;
        blankHeat[event.board().blankIndex()]++;
        if (events.size() < maxEvents) events.add(event);
        else truncated = true;
    }

    @Override
    public void onProgress(SearchMetrics liveMetrics) {
        latestMetrics = liveMetrics.copy();
    }

    List<SearchEvent> events() {
        return events;
    }

    long[] blankHeat() {
        return blankHeat.clone();
    }

    SearchMetrics latestMetrics() {
        return latestMetrics;
    }

    SearchEvent latestEvent() {
        return latestEvent;
    }

    boolean truncated() {
        return truncated;
    }
}
