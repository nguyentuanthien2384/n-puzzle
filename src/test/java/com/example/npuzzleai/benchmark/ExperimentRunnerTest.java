package com.example.npuzzleai.benchmark;

import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.search.SearchStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentRunnerTest {

    private static Dataset tiny() {
        return Datasets.withExactLengthsIfSmall(
                Datasets.walk("tiny-3x3", Goal.standard(3), 6, 14, new Random(1), 1L));
    }

    private static ExperimentConfig config(Path out, int threads) {
        return new ExperimentConfig("unit", "tiny-3x3", List.of("astar", "ida", "bfs"),
                List.of("manhattan", "linear-conflict"), 1, 0, 10_000, 0, 0, threads, 42L, true, out);
    }

    @Test
    void smallExperimentProducesCompleteFolder(@TempDir Path out) throws IOException {
        Dataset ds = tiny();
        ExperimentResult result = new ExperimentRunner().run(config(out, 1), ds, null, null);
        // astar × 2 heuristic + ida × 2 + bfs × 1 (không dùng heuristic) = 5 tổ hợp
        assertEquals(6 * 5, result.records().size());
        assertEquals(0, result.correctnessFailures());
        for (RunRecord r : result.records()) {
            assertEquals(SearchStatus.SOLVED, r.status());
            assertTrue(r.pathValid());
            assertEquals(Boolean.TRUE, r.optimalityOk(), r.combo() + " phải tối ưu");
            assertTrue(r.metrics().peakHeapBytes > 0, "Chạy 1 luồng phải đo heap đỉnh");
        }
        assertEquals(5, result.summary().size());

        Path dir = ExperimentExporter.export(result);
        for (String f : List.of("manifest.json", "environment.json", "puzzles.json", "dataset.txt",
                "raw-results.csv", "summary.csv", "charts/median-expanded.svg", "charts/median-time.svg", "logs/run.log")) {
            assertTrue(Files.isRegularFile(dir.resolve(f)), "Thiếu " + f);
        }
        List<String> raw = Files.readAllLines(dir.resolve("raw-results.csv"), StandardCharsets.UTF_8);
        assertEquals(1 + 30, raw.size());
        assertTrue(raw.get(0).startsWith("runOrder,algorithm,heuristic"));
        String manifest = Files.readString(dir.resolve("manifest.json"), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("\"seed\": 42"));
        assertTrue(manifest.contains(Long.toHexString(ds.checksum())));
        assertTrue(Files.readString(dir.resolve("charts/median-expanded.svg")).startsWith("<svg"));
    }

    @Test
    void parallelRunGivesSameSolutions(@TempDir Path out) {
        Dataset ds = tiny();
        ExperimentResult seq = new ExperimentRunner().run(config(out, 1), ds, null, null);
        ExperimentResult par = new ExperimentRunner().run(config(out, 4), ds, null, null);
        Map<String, Long> lengths = new HashMap<>();
        for (RunRecord r : seq.records()) lengths.put(r.combo() + "#" + r.instance(), r.solutionLength());
        for (RunRecord r : par.records()) {
            assertEquals(lengths.get(r.combo() + "#" + r.instance()), r.solutionLength());
            assertEquals(-1, r.metrics().peakHeapBytes, "Chạy song song không quy heap cho từng lần");
        }
    }

    @Test
    void unsolvableDatasetIsDetected(@TempDir Path out) {
        Dataset ds = Datasets.builtIn("unsolvable-3x3", 5L);
        ExperimentConfig cfg = new ExperimentConfig("unsolvable", "unsolvable-3x3", List.of("astar"),
                List.of("manhattan"), 1, 0, 5_000, 0, 0, 1, 5L, false, out);
        ExperimentResult result = new ExperimentRunner().run(cfg, ds, null, null);
        for (RunRecord r : result.records()) {
            assertEquals(SearchStatus.UNSOLVABLE, r.status());
            assertEquals(Boolean.TRUE, r.optimalityOk());
        }
        assertEquals(0, result.correctnessFailures());
    }

    @Test
    void datasetsAreDeterministicAndRoundTrip(@TempDir Path dir) throws IOException {
        Dataset a = Datasets.builtIn("hard-15p", 7L);
        assertEquals(a.checksum(), Datasets.builtIn("hard-15p", 7L).checksum());
        assertNotEquals(a.checksum(), Datasets.builtIn("hard-15p", 8L).checksum());

        Dataset exact = Datasets.builtIn("exact-3x3", 3L);
        assertNotNull(exact.optimalLength());
        Path file = dir.resolve("exact.txt");
        Datasets.write(exact, file);
        Dataset back = Datasets.read(file);
        assertEquals(exact.instances(), back.instances());
        assertEquals(exact.goal(), back.goal());
        assertArrayEquals(exact.optimalLength(), back.optimalLength());
        assertEquals(exact.checksum(), back.checksum());

        Dataset custom = Datasets.builtIn("custom-goal-3x3", 3L);
        assertEquals(Goal.CUSTOM, custom.goal().name());
    }

    @Test
    void consensusReferenceOn4x4(@TempDir Path out) {
        Dataset ds = Datasets.walk("tiny-4x4", Goal.standard(4), 4, 30, new Random(2), 2L);
        ExperimentConfig cfg = new ExperimentConfig("consensus", "tiny-4x4", List.of("astar", "ida", "wastar:3"),
                List.of("linear-conflict"), 1, 0, 20_000, 0, 0, 1, 2L, false, out);
        ExperimentResult result = new ExperimentRunner().run(cfg, ds, null, null);
        assertEquals(0, result.correctnessFailures());
        for (RunRecord r : result.records()) {
            assertTrue(r.optimalLength() >= 0, "4x4 phải có độ dài tham chiếu từ đồng thuận");
            if (r.claimsOptimal()) assertEquals(Boolean.TRUE, r.optimalityOk());
            else assertTrue(r.optimalityGap() >= 1.0 && r.optimalityGap() <= 3.0, "W-A* w=3: gap trong [1, 3]");
        }
    }

    @Test
    void consensusFlagsDisagreeingOptimalSolvers() {
        Dataset ds = Datasets.walk("fake", Goal.standard(4), 1, 10, new Random(1), 1L);
        RunRecord a = fake("astar", 20, true);
        RunRecord b = fake("buggy", 22, true);
        RunRecord c = fake("greedy", 30, false);
        List<RunRecord> checked = ExperimentRunner.applyConsensus(List.of(a, b, c), ds);
        assertEquals(Boolean.TRUE, checked.get(0).optimalityOk());
        assertEquals(Boolean.FALSE, checked.get(1).optimalityOk(), "Thuật toán 'tối ưu' dài hơn tham chiếu phải bị đánh dấu");
        assertNull(checked.get(2).optimalityOk());
        assertEquals(1.5, checked.get(2).optimalityGap(), 1e-12);
    }

    private static RunRecord fake(String algorithm, int length, boolean claimsOptimal) {
        com.example.npuzzleai.search.SearchMetrics m = new com.example.npuzzleai.search.SearchMetrics();
        m.solutionLength = length;
        return new RunRecord(1, algorithm, "h", 0, 1, SearchStatus.SOLVED, -1, true, null, m, "t", claimsOptimal);
    }

    @Test
    void statistics() {
        double[] v = {4, 1, 3, 2};
        assertEquals(2.5, Statistics.median(v), 1e-12);
        assertEquals(2.5, Statistics.mean(v), 1e-12);
        assertEquals(1.3, Statistics.percentile(v, 10), 1e-12);
        assertEquals(Math.sqrt(5.0 / 3.0), Statistics.stddev(v), 1e-12);
        assertTrue(Double.isNaN(Statistics.median(new double[0])));
    }

    @Test
    void jsonAndCsvEscaping() {
        String json = Json.write(Map.of("k", "a\"b\nc"));
        assertTrue(json.contains("\"a\\\"b\\nc\""));
        assertEquals("x,\"y,z\",\"q\"\"r\"\n", ExperimentExporter.csvLine(List.of("x", "y,z", "q\"r")));
    }
}
