package com.example.npuzzleai.benchmark;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.heuristics.pdb.AdditivePatternDatabaseHeuristic;
import com.example.npuzzleai.heuristics.pdb.PdbMetadata;
import com.example.npuzzleai.search.Heuristic;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Xuất thư mục thí nghiệm hoàn chỉnh:
 * <pre>
 * experiments/2026-10-07-153000-ten/
 *   manifest.json      cấu hình + dataset checksum + PDB checksum + Git commit
 *   environment.json   OS, CPU, RAM, JDK, JVM args, phiên bản dependency
 *   puzzles.json       các instance + độ dài tối ưu đã biết
 *   raw-results.csv    từng lần chạy
 *   summary.csv        tổng hợp theo tổ hợp (median, p10/p90, tỉ lệ tối ưu...)
 *   charts/*.svg       biểu đồ dùng ngay cho báo cáo
 *   logs/run.log       nhật ký
 * </pre>
 */
public final class ExperimentExporter {
    public static final String GENERATOR = "N-Puzzle Research & Teaching Platform 3.0";
    private static final DateTimeFormatter FOLDER_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss").withZone(ZoneId.systemDefault());

    private ExperimentExporter() {
    }

    public static Path export(ExperimentResult result) throws IOException {
        String safeName = result.config().name().replaceAll("[^A-Za-z0-9._-]+", "-");
        Path dir = result.config().outputRoot().resolve(FOLDER_TIME.format(result.startedAt()) + "-" + safeName);
        Files.createDirectories(dir.resolve("charts"));
        Files.createDirectories(dir.resolve("logs"));

        Files.writeString(dir.resolve("manifest.json"), Json.write(manifest(result)), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("environment.json"), Json.write(result.environment()), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("puzzles.json"), Json.write(puzzles(result.dataset())), StandardCharsets.UTF_8);
        Datasets.write(result.dataset(), dir.resolve("dataset.txt"));
        writeCsv(dir.resolve("raw-results.csv"), RunRecord.csvHeader(),
                result.records().stream().map(RunRecord::csvRow).toList());
        List<SummaryRow> summary = result.summary();
        writeCsv(dir.resolve("summary.csv"), SummaryRow.csvHeader(), summary.stream().map(SummaryRow::csvRow).toList());
        writeCharts(dir.resolve("charts"), summary);
        Files.write(dir.resolve("logs").resolve("run.log"), result.log(), StandardCharsets.UTF_8);
        return dir;
    }

    static Map<String, Object> manifest(ExperimentResult result) {
        ExperimentConfig c = result.config();
        Dataset ds = result.dataset();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("generator", GENERATOR);
        m.put("name", c.name());
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("name", ds.name());
        dataset.put("description", ds.description());
        dataset.put("size", ds.size());
        dataset.put("goal", ds.goal().board().toString());
        dataset.put("instances", ds.instances().size());
        dataset.put("seed", ds.seed());
        dataset.put("checksumCrc32", Long.toHexString(ds.checksum()));
        dataset.put("optimalLengthsKnown", ds.optimalLength() != null);
        m.put("dataset", dataset);
        m.put("algorithms", c.algorithms());
        m.put("heuristics", c.heuristics());
        m.put("repetitions", c.repetitions());
        m.put("warmupRuns", c.warmupRuns());
        m.put("timeoutMillis", c.timeoutMillis());
        m.put("maxExpandedNodes", c.maxExpanded());
        m.put("maxMemoryBytes", c.maxMemoryBytes());
        m.put("threads", c.threads());
        m.put("seed", c.seed());
        m.put("shuffledOrder", c.shuffleOrder());
        m.put("memoryMetricsValid", c.threads() == 1);
        m.put("jdk", System.getProperty("java.version"));
        m.put("jvmArgs", EnvironmentInfo.jvmArgs());
        m.put("gitCommit", result.environment().getOrDefault("gitCommit", "unknown"));
        Map<String, Object> pre = new LinkedHashMap<>();
        result.preprocessNs().forEach((k, v) -> pre.put(k, v / 1e6));
        m.put("preprocessingMs", pre);
        m.put("patternDatabases", pdbMetadata(result));
        m.put("startedAt", result.startedAt().toString());
        m.put("finishedAt", result.finishedAt().toString());
        m.put("totalRuns", result.records().size());
        m.put("cancelled", result.cancelled());
        m.put("correctnessFailures", result.correctnessFailures());
        return m;
    }

    private static List<Object> pdbMetadata(ExperimentResult result) {
        List<Object> list = new ArrayList<>();
        for (String spec : result.preprocessNs().keySet()) {
            Heuristic h = HeuristicRegistry.defaults().create(spec);
            if (!(h instanceof AdditivePatternDatabaseHeuristic pdb)) continue;
            for (PdbMetadata md : pdb.metadata()) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("heuristic", spec);
                e.put("patternTiles", md.patternTiles());
                e.put("entries", md.entries());
                e.put("checksumCrc32", Long.toHexString(md.checksum()));
                e.put("goalHash", Long.toHexString(md.goalHash()));
                e.put("costModel", md.costModel());
                e.put("builderVersion", md.builderVersion());
                e.put("buildMillis", md.buildMillis());
                list.add(e);
            }
        }
        return list;
    }

    private static List<Object> puzzles(Dataset ds) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < ds.instances().size(); i++) {
            Board b = ds.instances().get(i);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("index", i);
            e.put("tiles", b.toArray());
            e.put("optimalLength", ds.optimal(i));
            list.add(e);
        }
        return list;
    }

    private static void writeCharts(Path dir, List<SummaryRow> summary) throws IOException {
        List<String> labels = summary.stream().map(SummaryRow::combo).toList();
        Files.writeString(dir.resolve("median-expanded.svg"), SvgBarChart.horizontal("Số node mở rộng (trung vị)",
                "node, chỉ tính lần giải được", labels, summary.stream().map(SummaryRow::medianExpanded).toList(), true));
        Files.writeString(dir.resolve("median-time.svg"), SvgBarChart.horizontal("Thời gian (trung vị)",
                "mili giây", labels, summary.stream().map(SummaryRow::medianTimeMs).toList(), true));
        Files.writeString(dir.resolve("solved-rate.svg"), SvgBarChart.horizontal("Tỉ lệ giải được",
                "phần trăm", labels, summary.stream().map(r -> r.solvedRate() * 100).toList(), false));
        Files.writeString(dir.resolve("median-max-open.svg"), SvgBarChart.horizontal("Node giữ trong bộ nhớ (trung vị)",
                "maxOpen", labels, summary.stream().map(SummaryRow::medianMaxOpen).toList(), true));
    }

    public static void writeCsv(Path file, List<String> header, List<List<String>> rows) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(csvLine(header));
            for (List<String> row : rows) w.write(csvLine(row));
        }
    }

    static String csvLine(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            String c = cells.get(i) == null ? "" : cells.get(i);
            if (c.contains(",") || c.contains("\"") || c.contains("\n")) {
                sb.append('"').append(c.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(c);
            }
        }
        sb.append('\n');
        return sb.toString();
    }
}
