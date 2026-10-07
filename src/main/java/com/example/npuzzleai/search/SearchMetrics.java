package com.example.npuzzleai.search;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bộ đếm instrumentation của một lần tìm kiếm.
 *
 * <p>Trường public để vòng lặp nóng của thuật toán cập nhật không tốn chi phí gọi hàm.
 * Chỉ luồng tìm kiếm ghi; nơi khác muốn giữ lại số liệu trong lúc chạy phải dùng {@link #copy()}.</p>
 *
 * <ul>
 *   <li>expanded - node thực sự được mở rộng; generated - successor sinh ra.</li>
 *   <li>duplicates - successor bị loại vì trùng trạng thái đã biết với g không tốt hơn.</li>
 *   <li>reopened - trạng thái đã đóng được mở lại do tìm được g tốt hơn (A*).</li>
 *   <li>pruned - node bị cắt: vượt ngưỡng f (IDA*), bị bảng chuyển vị loại (IDA*-TT).</li>
 *   <li>iterations - số vòng lặp ngưỡng (IDA*); regenerated - số node mở rộng lại (IDA*: ngoài vòng cuối;
 *       RBFS: số lần quay lại một nhánh đã mở; SMA*: successor đã bị quên rồi sinh lại).</li>
 *   <li>evictions - node bị SMA* loại khỏi bộ nhớ.</li>
 *   <li>messages/workers/loadImbalancePct - tìm kiếm song song (HDA*): số trạng thái chuyển giữa các worker,
 *       số worker, và mất cân bằng tải = max/trung bình số node mở rộng mỗi worker × 100.</li>
 *   <li>heuristicTimeNs - <i>ước lượng</i> bằng cách đo 1/32 số lần gọi rồi nhân lại, để không làm sai lệch thời gian.</li>
 *   <li>peakHeapBytes/gcCount/gcTimeNs/preprocessTimeNs - do ExperimentRunner điền.</li>
 * </ul>
 */
public final class SearchMetrics {
    public long expanded;
    public long generated;
    public long duplicates;
    public long reopened;
    public long pruned;
    public long maxOpen;
    public long maxClosed;
    public long heuristicCalls;
    public long heuristicTimeNs;
    public long iterations;
    public long regenerated;
    public long recursiveCalls;
    public long maxDepth;
    public long fLimitUpdates;
    public long evictions;
    public long messages;
    public long workers = 1;
    public long loadImbalancePct = 100;
    public long solutionLength = -1;
    public long solutionCost = -1;
    public long wallTimeNs;
    public long cpuTimeNs = -1;
    public long preprocessTimeNs;
    public long peakHeapBytes = -1;
    public long gcCount = -1;
    public long gcTimeNs = -1;

    public SearchMetrics copy() {
        SearchMetrics m = new SearchMetrics();
        m.expanded = expanded;
        m.generated = generated;
        m.duplicates = duplicates;
        m.reopened = reopened;
        m.pruned = pruned;
        m.maxOpen = maxOpen;
        m.maxClosed = maxClosed;
        m.heuristicCalls = heuristicCalls;
        m.heuristicTimeNs = heuristicTimeNs;
        m.iterations = iterations;
        m.regenerated = regenerated;
        m.recursiveCalls = recursiveCalls;
        m.maxDepth = maxDepth;
        m.fLimitUpdates = fLimitUpdates;
        m.evictions = evictions;
        m.messages = messages;
        m.workers = workers;
        m.loadImbalancePct = loadImbalancePct;
        m.solutionLength = solutionLength;
        m.solutionCost = solutionCost;
        m.wallTimeNs = wallTimeNs;
        m.cpuTimeNs = cpuTimeNs;
        m.preprocessTimeNs = preprocessTimeNs;
        m.peakHeapBytes = peakHeapBytes;
        m.gcCount = gcCount;
        m.gcTimeNs = gcTimeNs;
        return m;
    }

    /** Toàn bộ bộ đếm theo thứ tự cố định - dùng cho CSV/JSON. */
    public Map<String, Long> toMap() {
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("expanded", expanded);
        map.put("generated", generated);
        map.put("duplicates", duplicates);
        map.put("reopened", reopened);
        map.put("pruned", pruned);
        map.put("maxOpen", maxOpen);
        map.put("maxClosed", maxClosed);
        map.put("heuristicCalls", heuristicCalls);
        map.put("heuristicTimeNs", heuristicTimeNs);
        map.put("iterations", iterations);
        map.put("regenerated", regenerated);
        map.put("recursiveCalls", recursiveCalls);
        map.put("maxDepth", maxDepth);
        map.put("fLimitUpdates", fLimitUpdates);
        map.put("evictions", evictions);
        map.put("messages", messages);
        map.put("workers", workers);
        map.put("loadImbalancePct", loadImbalancePct);
        map.put("solutionLength", solutionLength);
        map.put("solutionCost", solutionCost);
        map.put("wallTimeNs", wallTimeNs);
        map.put("cpuTimeNs", cpuTimeNs);
        map.put("preprocessTimeNs", preprocessTimeNs);
        map.put("peakHeapBytes", peakHeapBytes);
        map.put("gcCount", gcCount);
        map.put("gcTimeNs", gcTimeNs);
        return map;
    }

    public double wallTimeMillis() {
        return wallTimeNs / 1_000_000.0;
    }
}
