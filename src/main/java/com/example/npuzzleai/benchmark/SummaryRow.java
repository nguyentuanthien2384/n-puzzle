package com.example.npuzzleai.benchmark;

import com.example.npuzzleai.search.SearchStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tổng hợp theo tổ hợp thuật toán × heuristic - một dòng trong summary.csv và bảng kết quả chuẩn.
 */
public record SummaryRow(String algorithm,
                         String heuristic,
                         int runs,
                         int solved,
                         int timeouts,
                         int memoryFailures,
                         int unsolvableDetected,
                         double medianTimeMs,
                         double meanTimeMs,
                         double p10TimeMs,
                         double p90TimeMs,
                         double stdTimeMs,
                         double medianExpanded,
                         double meanExpanded,
                         double medianGenerated,
                         double medianMaxOpen,
                         double medianPeakHeapMb,
                         double meanLength,
                         double optimalRate,
                         double meanGap,
                         double preprocessMs,
                         int invalidPaths) {

    public String combo() {
        return heuristic.equals("none") ? algorithm : algorithm + " + " + heuristic;
    }

    public double solvedRate() {
        return runs == 0 ? 0 : (double) solved / runs;
    }

    /** Gom các lần chạy theo tổ hợp, giữ thứ tự xuất hiện của cấu hình. */
    public static List<SummaryRow> summarize(List<RunRecord> records, Map<String, Long> preprocessNs) {
        Map<String, List<RunRecord>> groups = new LinkedHashMap<>();
        for (RunRecord r : records) groups.computeIfAbsent(r.algorithm() + "\u0000" + r.heuristic(), k -> new ArrayList<>()).add(r);
        List<SummaryRow> rows = new ArrayList<>();
        for (List<RunRecord> group : groups.values()) rows.add(of(group, preprocessNs));
        return rows;
    }

    private static SummaryRow of(List<RunRecord> group, Map<String, Long> preprocessNs) {
        RunRecord first = group.get(0);
        List<Double> times = new ArrayList<>(), expanded = new ArrayList<>(), generated = new ArrayList<>(),
                maxOpen = new ArrayList<>(), heap = new ArrayList<>(), lengths = new ArrayList<>(), gaps = new ArrayList<>();
        int solved = 0, timeouts = 0, memory = 0, unsolvable = 0, optimalKnown = 0, optimalHits = 0, invalid = 0;
        for (RunRecord r : group) {
            SearchStatus s = r.status();
            if (s == SearchStatus.TIMEOUT || s == SearchStatus.NODE_LIMIT) timeouts++;
            if (s == SearchStatus.MEMORY_LIMIT) memory++;
            if (s == SearchStatus.UNSOLVABLE) unsolvable++;
            if (!r.solved()) continue;
            solved++;
            if (!r.pathValid()) invalid++;
            times.add(r.metrics().wallTimeNs / 1e6);
            expanded.add((double) r.metrics().expanded);
            generated.add((double) r.metrics().generated);
            maxOpen.add((double) r.metrics().maxOpen);
            if (r.metrics().peakHeapBytes >= 0) heap.add(r.metrics().peakHeapBytes / (1024.0 * 1024.0));
            lengths.add((double) r.metrics().solutionLength);
            if (r.optimalLength() >= 0) {
                optimalKnown++;
                if (r.metrics().solutionLength == r.optimalLength()) optimalHits++;
                double gap = r.optimalityGap();
                if (!Double.isNaN(gap)) gaps.add(gap);
            }
        }
        double[] t = Statistics.toArray(times);
        Long pre = preprocessNs.get(first.heuristic());
        return new SummaryRow(first.algorithm(), first.heuristic(), group.size(), solved, timeouts, memory, unsolvable,
                Statistics.median(t), Statistics.mean(t), Statistics.percentile(t, 10), Statistics.percentile(t, 90),
                Statistics.stddev(t),
                Statistics.median(Statistics.toArray(expanded)), Statistics.mean(Statistics.toArray(expanded)),
                Statistics.median(Statistics.toArray(generated)), Statistics.median(Statistics.toArray(maxOpen)),
                heap.isEmpty() ? Double.NaN : Statistics.median(Statistics.toArray(heap)),
                Statistics.mean(Statistics.toArray(lengths)),
                optimalKnown == 0 ? Double.NaN : (double) optimalHits / optimalKnown,
                gaps.isEmpty() ? Double.NaN : Statistics.mean(Statistics.toArray(gaps)),
                pre == null ? 0 : pre / 1e6, invalid);
    }

    public static List<String> csvHeader() {
        return List.of("algorithm", "heuristic", "runs", "solved", "solvedRate", "timeouts", "memoryFailures",
                "unsolvableDetected", "medianTimeMs", "meanTimeMs", "p10TimeMs", "p90TimeMs", "stdTimeMs",
                "medianExpanded", "meanExpanded", "medianGenerated", "medianMaxOpen", "medianPeakHeapMb",
                "meanLength", "optimalRate", "meanOptimalityGap", "preprocessMs", "invalidPaths");
    }

    public List<String> csvRow() {
        return List.of(algorithm, heuristic, String.valueOf(runs), String.valueOf(solved), num(solvedRate()),
                String.valueOf(timeouts), String.valueOf(memoryFailures), String.valueOf(unsolvableDetected),
                num(medianTimeMs), num(meanTimeMs), num(p10TimeMs), num(p90TimeMs), num(stdTimeMs),
                num(medianExpanded), num(meanExpanded), num(medianGenerated), num(medianMaxOpen),
                num(medianPeakHeapMb), num(meanLength), num(optimalRate), num(meanGap), num(preprocessMs),
                String.valueOf(invalidPaths));
    }

    static String num(double v) {
        return Double.isNaN(v) ? "" : String.format(Locale.ROOT, "%.4f", v);
    }
}
