package com.example.npuzzleai.cli;

import com.example.npuzzleai.algorithms.AlgorithmRegistry;
import com.example.npuzzleai.benchmark.Dataset;
import com.example.npuzzleai.benchmark.Datasets;
import com.example.npuzzleai.benchmark.ExperimentConfig;
import com.example.npuzzleai.benchmark.ExperimentExporter;
import com.example.npuzzleai.benchmark.ExperimentResult;
import com.example.npuzzleai.benchmark.ExperimentRunner;
import com.example.npuzzleai.benchmark.SummaryRow;
import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.BoardGenerator;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Move;
import com.example.npuzzleai.core.PuzzleProblem;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.heuristics.pdb.DefaultPartitions;
import com.example.npuzzleai.heuristics.pdb.PatternDatabase;
import com.example.npuzzleai.heuristics.pdb.PatternDatabaseBuilder;
import com.example.npuzzleai.heuristics.pdb.PatternDefinition;
import com.example.npuzzleai.heuristics.pdb.PdbStore;
import com.example.npuzzleai.learning.FeatureExtractor;
import com.example.npuzzleai.learning.HeuristicTrainer;
import com.example.npuzzleai.research.AdversarialGenerator;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.SearchAlgorithm;
import com.example.npuzzleai.search.SearchBudget;
import com.example.npuzzleai.search.SearchMetrics;
import com.example.npuzzleai.search.SearchObserver;
import com.example.npuzzleai.search.SearchResult;
import com.example.npuzzleai.verify.ExactDistanceTable;
import com.example.npuzzleai.verify.HeuristicReport;
import com.example.npuzzleai.verify.HeuristicVerifier;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Giao diện dòng lệnh - chạy solver, kiểm định heuristic, benchmark và dựng PDB mà không cần JavaFX.
 *
 * <pre>
 *   list
 *   solve     --board "1 2 3 4 5 6 0 7 8" [--goal standard] [--algo ida] [--heuristic apdb] [--timeout 60]
 *   verify    [--goal standard|blank-first] [--heuristics a,b,c] [--csv file]
 *   benchmark --dataset easy-3x3 --algos astar,ida --heuristics manhattan,apdb [--reps 3] [--threads 1] ...
 *   dataset   --name hard-15p [--seed 20261006] --out file.txt
 *   build-pdb --size 4 [--goal standard] [--partition "1,2,5,6,9;3,4,7,8,11;10,12,13,14,15"] [--dir pdb-cache]
 *   plugins   --dir plugins
 * </pre>
 */
public final class NPuzzleCli {
    private final PrintStream out;

    public NPuzzleCli(PrintStream out) {
        this.out = out;
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        int code;
        try {
            code = new NPuzzleCli(out).run(args);
        } catch (IllegalArgumentException e) {
            out.println("Lỗi: " + e.getMessage());
            code = 2;
        } catch (IOException e) {
            out.println("Lỗi I/O: " + e.getMessage());
            code = 3;
        }
        System.exit(code);
    }

    public int run(String[] args) throws IOException {
        if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
            printHelp();
            return 0;
        }
        Map<String, String> opt = parseOptions(Arrays.copyOfRange(args, 1, args.length));
        return switch (args[0]) {
            case "list" -> list();
            case "solve" -> solve(opt);
            case "verify" -> verify(opt);
            case "benchmark" -> benchmark(opt);
            case "dataset" -> dataset(opt);
            case "build-pdb" -> buildPdb(opt);
            case "plugins" -> plugins(opt);
            case "train-heuristic" -> trainHeuristic(opt);
            case "adversarial" -> adversarial(opt);
            default -> {
                out.println("Lệnh không hợp lệ: " + args[0]);
                printHelp();
                yield 2;
            }
        };
    }

    private void printHelp() {
        out.println("""
                N-Puzzle Research & Teaching Platform - CLI

                Lệnh:
                  list                                     Liệt kê thuật toán và heuristic
                  solve --board "<ô...>" [tuỳ chọn]         Giải một bảng
                      --goal standard|blank-first|"<ô...>" (mặc định standard)
                      --algo <mã> (mặc định ida)  --heuristic <mã> (mặc định linear-conflict)
                      --timeout <giây> --max-nodes <n> --max-mem-mb <MB> --show-path
                      --random <size> --steps <n> --seed <s>   (sinh bảng ngẫu nhiên thay cho --board)
                  verify [--goal ...] [--heuristics a,b] [--size 3] [--csv file]
                                                           Kiểm định vét cạn h <= h* và nhất quán
                  benchmark --dataset <tên|file:đường_dẫn> --algos a,b --heuristics x,y
                      [--reps 1] [--warmup 1] [--timeout 30] [--max-nodes n] [--max-mem-mb MB]
                      [--threads 1] [--seed 20261006] [--no-shuffle] [--out experiments] [--name tên]
                  dataset --name <tên> [--seed s] --out file.txt   Xuất dataset dựng sẵn
                  build-pdb --size 4 [--goal ...] [--partition "1,2,3;4,5,6"] [--dir pdb-cache]
                  plugins --dir plugins                    Nạp JAR plugin và liệt kê
                  train-heuristic --size 3|4 [--out models/learned-4x4.model] [--instances 300]
                      [--min-walk 20] [--max-walk 70] [--hidden 16] [--epochs 25] [--seed s] [--no-pdb-feature]
                                                           Huấn luyện heuristic mạng nơ-ron
                  adversarial --size 3|4 [--objective expansions|gap] [--algo astar] [--heuristic manhattan]
                      [--population 30] [--generations 20] [--mutation 6] [--node-budget 200000]
                      [--seed s] [--top 10] [--out adversarial.txt]
                                                           Sinh puzzle khiến heuristic/solver gặp ca xấu

                Mã có tham số: wastar:2.0, sma:50000, ida-tt:64, hda:4, mcts:300, focal:1.5, focal:2:linear-conflict,
                               apdb:1,2,5,6,9;3,4,7,8,11;10,12,13,14,15, learned:models/learned-4x4.model
                Dataset dựng sẵn: """ + String.join(", ", Datasets.builtInNames().keySet()));
    }

    private int list() {
        out.println("THUẬT TOÁN (mã mặc định)");
        for (SearchAlgorithm a : AlgorithmRegistry.defaults().all()) {
            out.printf("  %-16s %-26s %s%n", a.id(), a.displayName(), a.description());
        }
        out.println();
        out.println("HEURISTIC   (A=admissible, C=consistent, P=cần tiền xử lý, ?=thử nghiệm)");
        for (Heuristic h : HeuristicRegistry.defaults().all()) {
            out.printf("  %-18s %-5s %-36s %s%n", h.id(), h.properties().flags(), h.displayName(), h.properties().note());
        }
        out.println();
        out.println("DATASET");
        Datasets.builtInNames().forEach((k, v) -> out.printf("  %-18s %s%n", k, v));
        return 0;
    }

    private int solve(Map<String, String> opt) {
        Board start;
        if (opt.containsKey("random")) {
            int size = intOpt(opt, "random", 3);
            Goal g = Goal.parse(opt.getOrDefault("goal", Goal.STANDARD), size);
            Random rnd = new Random(longOpt(opt, "seed", System.nanoTime()));
            start = opt.containsKey("steps") ? BoardGenerator.randomWalk(g, intOpt(opt, "steps", 40), rnd)
                    : BoardGenerator.uniformSolvable(g, rnd);
        } else {
            start = Board.parse(require(opt, "board"));
        }
        Goal goal = Goal.parse(opt.getOrDefault("goal", Goal.STANDARD), start.size());
        SearchAlgorithm algo = AlgorithmRegistry.defaults().create(opt.getOrDefault("algo", "ida"));
        Heuristic h = HeuristicRegistry.defaults().create(opt.getOrDefault("heuristic", "linear-conflict"));
        SearchBudget budget = new SearchBudget(longOpt(opt, "timeout", 60) * 1000L, longOpt(opt, "max-nodes", 0),
                longOpt(opt, "max-mem-mb", 0) * 1024L * 1024L);

        out.println("Bắt đầu:");
        out.print(start.toGrid());
        out.println("Đích: " + goal);
        out.println("Thuật toán: " + algo.displayName() + " | Heuristic: "
                + (algo.properties().usesHeuristic() ? h.displayName() : "không dùng"));
        long t0 = System.nanoTime();
        if (algo.properties().usesHeuristic() && h.supports(start.size())) h.prepare(goal);
        long pre = System.nanoTime() - t0;
        if (pre > 5_000_000) out.printf("Tiền xử lý heuristic: %.1f ms%n", pre / 1e6);

        SearchResult r = algo.solve(new PuzzleProblem(start, goal), h, budget, SearchObserver.NONE);
        out.println();
        out.println("Trạng thái: " + r.status() + " - " + r.message());
        printMetrics(r.metrics());
        if (r.solved()) {
            out.println("Lời giải (" + r.length() + " bước, ký hiệu = hướng đi của ô trống): " + Move.format(r.moves()));
            if (start.size() <= 3) {
                int exact = ExactDistanceTable.forGoal(goal).distance(start);
                out.println("Độ dài tối ưu chính xác (BFS ngược): " + exact
                        + (exact == r.length() ? "  ✓ tối ưu" : "  ✗ không tối ưu"));
            }
            if (opt.containsKey("show-path")) {
                int i = 0;
                for (Board b : r.path()) {
                    out.println("Bước " + (i++) + ":");
                    out.print(b.toGrid());
                }
            }
        }
        return r.solved() ? 0 : 1;
    }

    private void printMetrics(SearchMetrics m) {
        out.printf(Locale.ROOT, "  expanded=%,d  generated=%,d  duplicates=%,d  reopened=%,d  pruned=%,d%n",
                m.expanded, m.generated, m.duplicates, m.reopened, m.pruned);
        out.printf(Locale.ROOT, "  maxOpen=%,d  maxClosed=%,d  maxDepth=%d  iterations=%d  regenerated=%,d  evictions=%,d%n",
                m.maxOpen, m.maxClosed, m.maxDepth, m.iterations, m.regenerated, m.evictions);
        out.printf(Locale.ROOT, "  heuristicCalls=%,d  heuristicTime~%.1f ms  wall=%.2f ms  cpu=%.2f ms%n",
                m.heuristicCalls, m.heuristicTimeNs / 1e6, m.wallTimeNs / 1e6, m.cpuTimeNs / 1e6);
    }

    private int verify(Map<String, String> opt) throws IOException {
        int size = intOpt(opt, "size", 3);
        Goal goal = Goal.parse(opt.getOrDefault("goal", Goal.STANDARD), size);
        List<String> ids = opt.containsKey("heuristics") ? csv(opt.get("heuristics"))
                : HeuristicRegistry.defaults().defaultIds();
        ExactDistanceTable table = ExactDistanceTable.forGoal(goal);
        out.printf("Kiểm định vét cạn trên %,d trạng thái tới được (đích %s, đường kính %d)%n%n",
                table.reachableCount(), goal, table.maxDistance());
        out.printf("%-18s %-5s %10s %8s %12s %8s %8s %8s  %s%n", "heuristic", "claim", "h>h*", "max+",
                "inconsist.", "exact%", "mean h", "mean err", "kết luận");
        List<HeuristicReport> reports = new ArrayList<>();
        boolean allClaimsHold = true;
        for (String id : ids) {
            Heuristic h = HeuristicRegistry.defaults().create(id);
            if (!h.supports(size)) {
                out.printf("%-18s (không hỗ trợ %dx%d)%n", id, size, size);
                continue;
            }
            HeuristicReport r = HeuristicVerifier.verify(h, table);
            reports.add(r);
            allClaimsHold &= r.claimsHold();
            out.printf(Locale.ROOT, "%-18s %-5s %,10d %8d %,12d %7.2f%% %8.2f %8.2f  %s%n", r.heuristicId(),
                    h.properties().flags(), r.admissibilityViolations(), r.maxOverestimate(), r.consistencyViolations(),
                    r.exactRate() * 100, r.meanH(), r.meanError(), r.verdict());
            if (!r.counterexample().isEmpty()) {
                out.printf("    phản ví dụ: [%s] h=%d > h*=%d%n", r.counterexample(), r.counterexampleH(), r.counterexampleHStar());
            }
        }
        if (opt.containsKey("csv")) {
            List<List<String>> rows = new ArrayList<>();
            for (HeuristicReport r : reports) {
                rows.add(List.of(r.heuristicId(), r.goalName(), String.valueOf(r.states()), String.valueOf(r.edges()),
                        String.valueOf(r.admissibilityViolations()), String.valueOf(r.maxOverestimate()),
                        String.valueOf(r.consistencyViolations()), fmt(r.meanH()), fmt(r.meanHStar()),
                        fmt(r.meanError()), fmt(r.medianError()), fmt(r.exactRate()),
                        String.valueOf(r.claimedAdmissible()), String.valueOf(r.claimedConsistent()), r.verdict(),
                        r.counterexample()));
            }
            ExperimentExporter.writeCsv(Path.of(opt.get("csv")), List.of("heuristic", "goal", "states", "edges",
                    "admissibilityViolations", "maxOverestimate", "consistencyViolations", "meanH", "meanHStar",
                    "meanError", "medianError", "exactRate", "claimedAdmissible", "claimedConsistent", "verdict",
                    "counterexample"), rows);
            out.println("\nĐã ghi " + opt.get("csv"));
        }
        out.println(allClaimsHold ? "\nMọi khai báo tính chất đều đúng." : "\nCÓ HEURISTIC KHAI BÁO SAI TÍNH CHẤT!");
        return allClaimsHold ? 0 : 1;
    }

    private int benchmark(Map<String, String> opt) throws IOException {
        long seed = longOpt(opt, "seed", Datasets.DEFAULT_SEED);
        String datasetSpec = opt.getOrDefault("dataset", "easy-3x3");
        String defaultName = datasetSpec.startsWith("file:")
                ? Path.of(datasetSpec.substring(5)).getFileName().toString().replaceFirst("\\.[^.]*$", "")
                : datasetSpec;
        ExperimentConfig config = new ExperimentConfig(
                opt.getOrDefault("name", defaultName),
                datasetSpec,
                csv(opt.getOrDefault("algos", "astar,ida")),
                csv(opt.getOrDefault("heuristics", "manhattan,linear-conflict")),
                intOpt(opt, "reps", 1), intOpt(opt, "warmup", 1),
                longOpt(opt, "timeout", 30) * 1000L, longOpt(opt, "max-nodes", 0),
                longOpt(opt, "max-mem-mb", 0) * 1024L * 1024L,
                intOpt(opt, "threads", 1), seed, !opt.containsKey("no-shuffle"),
                Path.of(opt.getOrDefault("out", "experiments")));
        Dataset dataset = Datasets.resolve(datasetSpec, seed);
        out.printf("Dataset %s: %d instance %dx%d (checksum %s)%n", dataset.name(), dataset.instances().size(),
                dataset.size(), dataset.size(), Long.toHexString(dataset.checksum()));

        ExperimentResult result = new ExperimentRunner().run(config, dataset, new ExperimentRunner.Listener() {
            @Override
            public void onRunCompleted(com.example.npuzzleai.benchmark.RunRecord r, int completed, int total) {
                if (completed % Math.max(1, total / 20) == 0 || completed == total) {
                    out.printf("  [%d/%d] %s #%d → %s%n", completed, total, r.combo(), r.instance(), r.status());
                }
            }

            @Override
            public void onMessage(String message) {
                out.println("  " + message);
            }
        }, new AtomicBoolean());

        Path dir = ExperimentExporter.export(result);
        out.println();
        out.printf("%-34s %7s %10s %12s %10s %8s %8s%n", "tổ hợp", "giải", "median ms", "median exp", "maxOpen", "len", "tối ưu");
        for (SummaryRow s : result.summary()) {
            out.printf(Locale.ROOT, "%-34s %3d/%-3d %10.2f %12.0f %10.0f %8.2f %8s%n", s.combo(), s.solved(), s.runs(),
                    s.medianTimeMs(), s.medianExpanded(), s.medianMaxOpen(), s.meanLength(),
                    Double.isNaN(s.optimalRate()) ? "-" : String.format(Locale.ROOT, "%.0f%%", s.optimalRate() * 100));
        }
        out.println("\nKết quả: " + dir.toAbsolutePath());
        long failures = result.correctnessFailures();
        if (failures > 0) out.println("CẢNH BÁO: " + failures + " lần chạy sai tính đúng đắn/tối ưu (xem raw-results.csv)");
        return failures == 0 ? 0 : 1;
    }

    private int dataset(Map<String, String> opt) throws IOException {
        Dataset ds = Datasets.builtIn(require(opt, "name"), longOpt(opt, "seed", Datasets.DEFAULT_SEED));
        Path file = Path.of(opt.getOrDefault("out", ds.name() + ".txt"));
        Datasets.write(ds, file);
        out.println("Đã ghi " + ds.instances().size() + " instance vào " + file.toAbsolutePath());
        return 0;
    }

    private int buildPdb(Map<String, String> opt) throws IOException {
        int size = intOpt(opt, "size", 4);
        Goal goal = Goal.parse(opt.getOrDefault("goal", Goal.STANDARD), size);
        List<PatternDefinition> patterns = opt.containsKey("partition")
                ? PatternDefinition.parsePartition(opt.get("partition"), size)
                : DefaultPartitions.forGoal(goal);
        PatternDefinition.requireDisjoint(patterns);
        Path dir = Path.of(opt.getOrDefault("dir", "pdb-cache"));
        for (PatternDefinition p : patterns) {
            out.println("Dựng pattern " + p + " ...");
            PatternDatabase db = PatternDatabaseBuilder.build(p, goal);
            Path file = dir.resolve(PdbStore.fileName(p, goal));
            PdbStore.write(db, file);
            out.println("  " + db.metadata());
            out.println("  → " + file.toAbsolutePath());
        }
        return 0;
    }

    private int plugins(Map<String, String> opt) throws IOException {
        Path dir = Path.of(opt.getOrDefault("dir", "plugins"));
        int a = AlgorithmRegistry.defaults().loadPlugins(dir);
        int h = HeuristicRegistry.defaults().loadPlugins(dir);
        out.println("Đã nạp " + a + " thuật toán và " + h + " heuristic từ " + dir.toAbsolutePath());
        return list();
    }

    private int trainHeuristic(Map<String, String> opt) throws IOException {
        int size = intOpt(opt, "size", 3);
        Goal goal = Goal.parse(opt.getOrDefault("goal", Goal.STANDARD), size);
        HeuristicTrainer.Options defaults = HeuristicTrainer.Options.defaults();
        HeuristicTrainer.Options options = new HeuristicTrainer.Options(intOpt(opt, "hidden", defaults.hidden()),
                intOpt(opt, "epochs", defaults.epochs()), defaults.batchSize(), defaults.learningRate(),
                intOpt(opt, "max-samples", defaults.maxSamples()), longOpt(opt, "seed", defaults.seed()));
        HeuristicTrainer.Report report;
        if (size <= 3) {
            out.println("Huấn luyện trên nhãn h* chính xác của toàn bộ không gian " + size + "x" + size + "...");
            report = HeuristicTrainer.trainExact(goal, options);
        } else {
            int instances = intOpt(opt, "instances", 300);
            out.println("Giải tối ưu " + instances + " bài (IDA* + PDB) để lấy nhãn...");
            FeatureExtractor.FeatureSet set = opt.containsKey("no-pdb-feature")
                    ? FeatureExtractor.FeatureSet.BASIC : FeatureExtractor.FeatureSet.BASIC_PDB;
            report = HeuristicTrainer.trainFromSolver(goal, instances, intOpt(opt, "min-walk", 20),
                    intOpt(opt, "max-walk", 70), set, options, n -> {
                        if (n % 50 == 0) out.println("  đã giải " + n + " bài");
                    });
        }
        out.println("Kết quả: " + report);
        Path file = Path.of(opt.getOrDefault("out", "models/learned-" + size + "x" + size + ".model"));
        report.model().save(file);
        out.println("Đã lưu mô hình: " + file.toAbsolutePath());
        out.println("Dùng: --heuristic learned:" + file + "   hoặc   --algo focal:1.5 (mặc định đọc models/learned-NxN.model)");
        return 0;
    }

    private int adversarial(Map<String, String> opt) throws IOException {
        int size = intOpt(opt, "size", 3);
        Goal goal = Goal.parse(opt.getOrDefault("goal", Goal.STANDARD), size);
        AdversarialGenerator.Objective objective = opt.getOrDefault("objective", "expansions").startsWith("gap")
                ? AdversarialGenerator.Objective.HEURISTIC_GAP : AdversarialGenerator.Objective.EXPANSIONS;
        AdversarialGenerator.Config config = new AdversarialGenerator.Config(goal, objective,
                opt.getOrDefault("algo", "astar"), opt.getOrDefault("heuristic", "manhattan"),
                intOpt(opt, "population", 30), intOpt(opt, "generations", 20), intOpt(opt, "mutation", 6),
                longOpt(opt, "node-budget", 200_000), longOpt(opt, "seed", Datasets.DEFAULT_SEED));
        out.printf("Tiến hoá %s cho %s + %s trên %dx%d (quần thể %d, %d thế hệ)%n", objective, config.algorithm(),
                config.heuristic(), size, size, config.population(), config.generations());
        AdversarialGenerator generator = new AdversarialGenerator(config);
        List<AdversarialGenerator.Candidate> result = generator.run((gen, best, mean) ->
                out.printf(Locale.ROOT, "  thế hệ %3d: tốt nhất %.0f, trung bình %.1f%n", gen, best.fitness(), mean), null);
        int top = Math.min(intOpt(opt, "top", 10), result.size());
        out.printf("%nTop %d (đã đánh giá %d trạng thái):%n", top, generator.evaluations());
        out.printf("%-10s %6s %6s %10s  %s%n", "fitness", "h", "h*", "expanded", "trạng thái");
        for (int i = 0; i < top; i++) {
            AdversarialGenerator.Candidate c = result.get(i);
            out.printf(Locale.ROOT, "%-10.0f %6d %6s %,10d  %s%n", c.fitness(), c.heuristicValue(),
                    c.optimalLength() < 0 ? "?" : String.valueOf(c.optimalLength()), c.expanded(), c.board());
        }
        if (opt.containsKey("out")) {
            Dataset ds = AdversarialGenerator.toDataset("adversarial-" + objective.name().toLowerCase(Locale.ROOT),
                    goal, result.subList(0, top), config.seed());
            Path file = Path.of(opt.get("out"));
            Datasets.write(ds, file);
            out.println("\nĐã ghi dataset: " + file.toAbsolutePath() + "  (benchmark: --dataset file:" + file + ")");
        }
        return 0;
    }

    static Map<String, String> parseOptions(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) throw new IllegalArgumentException("Tham số không hợp lệ: " + a);
            String key = a.substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) map.put(key, args[++i]);
            else map.put(key, "true");
        }
        return map;
    }

    private static String require(Map<String, String> opt, String key) {
        String v = opt.get(key);
        if (v == null || v.equals("true")) throw new IllegalArgumentException("Thiếu --" + key);
        return v;
    }

    private static int intOpt(Map<String, String> opt, String key, int def) {
        return opt.containsKey(key) ? Integer.parseInt(opt.get(key)) : def;
    }

    private static long longOpt(Map<String, String> opt, String key, long def) {
        return opt.containsKey(key) ? Long.parseLong(opt.get(key)) : def;
    }

    /**
     * Tách danh sách mã theo dấu phẩy. Tham số partition của apdb/pdb cũng chứa dấu phẩy
     * ("apdb:1,2,3;4,5,6"), nên token bắt đầu bằng chữ số được nối lại vào mã đứng trước -
     * tên thuật toán/heuristic không bao giờ bắt đầu bằng chữ số.
     */
    static List<String> csv(String s) {
        List<String> list = new ArrayList<>();
        for (String raw : s.split(",")) {
            String token = raw.trim();
            if (token.isEmpty()) continue;
            if (!list.isEmpty() && Character.isDigit(token.charAt(0))) {
                list.set(list.size() - 1, list.get(list.size() - 1) + "," + token);
            } else {
                list.add(token);
            }
        }
        return list;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }
}
