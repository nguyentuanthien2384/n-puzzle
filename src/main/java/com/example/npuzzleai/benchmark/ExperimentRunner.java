package com.example.npuzzleai.benchmark;

import com.example.npuzzleai.algorithms.AlgorithmRegistry;
import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.PuzzleProblem;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.SearchAlgorithm;
import com.example.npuzzleai.search.SearchBudget;
import com.example.npuzzleai.search.SearchObserver;
import com.example.npuzzleai.search.SearchResult;
import com.example.npuzzleai.search.SearchStatus;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chạy thí nghiệm end-to-end: tiền xử lý heuristic (đo riêng), warm-up, chạy mọi tổ hợp
 * thuật toán × heuristic × instance × lần lặp, kiểm chứng đường đi và tính tối ưu.
 *
 * <p>Song song hoá ở mức <i>thí nghiệm độc lập</i> (mỗi lần chạy một luồng), không song song hoá
 * một lần tìm kiếm. Executor riêng có số luồng cố định để số worker là biến thí nghiệm tường minh
 * thay vì vô tình dùng chung ForkJoinPool.commonPool().</p>
 *
 * <p>Heap đỉnh và GC chỉ được đo khi threads = 1 (khi chạy song song, heap là tài nguyên chung
 * nên không quy được cho từng lần chạy - các cột này để -1).</p>
 */
public final class ExperimentRunner {

    /** Nhận tiến độ; được gọi từ luồng worker. */
    public interface Listener {
        void onRunCompleted(RunRecord record, int completed, int total);

        default void onMessage(String message) {
        }
    }

    private final AlgorithmRegistry algorithms;
    private final HeuristicRegistry heuristics;

    public ExperimentRunner() {
        this(AlgorithmRegistry.defaults(), HeuristicRegistry.defaults());
    }

    public ExperimentRunner(AlgorithmRegistry algorithms, HeuristicRegistry heuristics) {
        this.algorithms = algorithms;
        this.heuristics = heuristics;
    }

    private record Combo(SearchAlgorithm algorithm, Heuristic heuristic, String heuristicId) {
    }

    private record Task(int instance, int repetition, Combo combo) {
    }

    public ExperimentResult run(ExperimentConfig config, Dataset dataset, Listener listener, AtomicBoolean cancelled) {
        Listener out = listener == null ? (r, c, t) -> { } : listener;
        AtomicBoolean stop = cancelled == null ? new AtomicBoolean() : cancelled;
        Instant startedAt = Instant.now();
        int size = dataset.size();
        List<String> log = Collections.synchronizedList(new ArrayList<>());

        // 1. Dựng tổ hợp hợp lệ.
        List<Combo> combos = new ArrayList<>();
        for (String algoSpec : config.algorithms()) {
            SearchAlgorithm algo = algorithms.create(algoSpec);
            if (!algo.supports(size)) {
                message(out, log, "Bỏ qua " + algo.id() + ": không hỗ trợ " + size + "x" + size);
                continue;
            }
            if (!algo.properties().usesHeuristic()) {
                combos.add(new Combo(algo, null, "none"));
                continue;
            }
            for (String hSpec : config.heuristics()) {
                Heuristic h = heuristics.create(hSpec);
                if (!h.supports(size)) {
                    message(out, log, "Bỏ qua " + h.id() + ": không hỗ trợ " + size + "x" + size);
                    continue;
                }
                combos.add(new Combo(algo, h, h.id()));
            }
        }
        if (combos.isEmpty()) throw new IllegalArgumentException("Không có tổ hợp thuật toán × heuristic nào hợp lệ");

        // 2. Tiền xử lý heuristic (dựng/nạp PDB...) - đo riêng, không tính vào thời gian tìm kiếm.
        Map<String, Long> preprocessNs = new LinkedHashMap<>();
        for (Combo c : combos) {
            if (c.heuristic() == null || preprocessNs.containsKey(c.heuristicId())) continue;
            long t0 = System.nanoTime();
            c.heuristic().prepare(dataset.goal());
            long dt = System.nanoTime() - t0;
            preprocessNs.put(c.heuristicId(), dt);
            message(out, log, String.format("Tiền xử lý %s: %.1f ms", c.heuristicId(), dt / 1e6));
        }

        SearchBudget budget = new SearchBudget(config.timeoutMillis(), config.maxExpanded(), config.maxMemoryBytes());
        SearchObserver observer = SearchObserver.cancellable(stop::get);

        // 3. Warm-up (không ghi) để JIT biên dịch code nóng trước khi đo.
        if (config.warmupRuns() > 0 && !dataset.instances().isEmpty()) {
            PuzzleProblem warm = new PuzzleProblem(dataset.instances().get(0), dataset.goal());
            for (Combo c : combos) {
                for (int i = 0; i < config.warmupRuns() && !stop.get(); i++) {
                    c.algorithm().solve(warm, c.heuristic(), budget, observer);
                }
            }
            message(out, log, "Hoàn tất warm-up " + config.warmupRuns() + " lần/tổ hợp");
        }

        // 4. Danh sách lần chạy, xáo thứ tự có seed.
        List<Task> tasks = new ArrayList<>();
        for (int rep = 1; rep <= config.repetitions(); rep++) {
            for (int i = 0; i < dataset.instances().size(); i++) {
                for (Combo c : combos) tasks.add(new Task(i, rep, c));
            }
        }
        if (config.shuffleOrder()) Collections.shuffle(tasks, new Random(config.seed()));

        int total = tasks.size();
        boolean measureMemory = config.threads() == 1;
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger order = new AtomicInteger();
        List<RunRecord> records = Collections.synchronizedList(new ArrayList<>(total));
        ExecutorService executor = Executors.newFixedThreadPool(config.threads(), namedThreads());
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>(total);
            for (Task task : tasks) {
                futures.add(CompletableFuture.runAsync(() -> {
                    if (stop.get()) return;
                    RunRecord r = execute(task, dataset, budget, observer, measureMemory, order.incrementAndGet(),
                            preprocessNs);
                    records.add(r);
                    out.onRunCompleted(r, completed.incrementAndGet(), total);
                }, executor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdownNow();
        }
        if (stop.get()) message(out, log, "Thí nghiệm bị dừng sau " + records.size() + "/" + total + " lần chạy");

        List<RunRecord> sorted = new ArrayList<>(records);
        Map<String, Integer> comboOrder = new LinkedHashMap<>();
        for (Combo c : combos) comboOrder.putIfAbsent(c.algorithm().id() + "\u0000" + c.heuristicId(), comboOrder.size());
        sorted.sort(Comparator.comparingInt((RunRecord r) -> comboOrder.getOrDefault(r.algorithm() + "\u0000" + r.heuristic(), 0))
                .thenComparingInt(RunRecord::instance)
                .thenComparingInt(RunRecord::repetition));
        return new ExperimentResult(config, dataset, sorted, preprocessNs, EnvironmentInfo.collect(),
                startedAt, Instant.now(), stop.get(), List.copyOf(log));
    }

    private RunRecord execute(Task task, Dataset dataset, SearchBudget budget, SearchObserver observer,
                              boolean measureMemory, int runOrder, Map<String, Long> preprocessNs) {
        Board start = dataset.instances().get(task.instance());
        PuzzleProblem problem = new PuzzleProblem(start, dataset.goal());
        long gcCountBefore = 0, gcTimeBefore = 0;
        if (measureMemory) {
            resetPeakHeap();
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                gcCountBefore += Math.max(0, gc.getCollectionCount());
                gcTimeBefore += Math.max(0, gc.getCollectionTime());
            }
        }
        SearchResult result = task.combo().algorithm().solve(problem, task.combo().heuristic(), budget, observer);
        if (measureMemory) {
            long gcCount = 0, gcTime = 0;
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                gcCount += Math.max(0, gc.getCollectionCount());
                gcTime += Math.max(0, gc.getCollectionTime());
            }
            result.metrics().peakHeapBytes = peakHeap();
            result.metrics().gcCount = gcCount - gcCountBefore;
            result.metrics().gcTimeNs = (gcTime - gcTimeBefore) * 1_000_000L;
        }
        Long pre = preprocessNs.get(task.combo().heuristicId());
        if (pre != null) result.metrics().preprocessTimeNs = pre;

        int optimal = dataset.optimal(task.instance());
        boolean known = dataset.optimalLength() != null;
        boolean pathValid = result.pathReachesGoal();
        Boolean optimalityOk = null;
        if (known && optimal >= 0 && result.status() == SearchStatus.UNSOLVABLE) {
            optimalityOk = false; // báo vô nghiệm cho một instance khả giải
        } else if (result.solved() && optimal >= 0 && claimsOptimal(task.combo())) {
            optimalityOk = result.length() == optimal;
        } else if (known && optimal < 0) {
            // Bảng chính xác xác nhận không tới được: chỉ đúng khi solver báo vô nghiệm.
            optimalityOk = result.status() == SearchStatus.UNSOLVABLE;
        }
        return new RunRecord(runOrder, task.combo().algorithm().id(), task.combo().heuristicId(), task.instance(),
                task.repetition(), result.status(), optimal, pathValid, optimalityOk, result.metrics(),
                Thread.currentThread().getName());
    }

    /** Tổ hợp có cam kết tối ưu: thuật toán tối ưu + heuristic khai báo chấp nhận được (hoặc không dùng h). */
    private static boolean claimsOptimal(Combo combo) {
        if (!combo.algorithm().properties().optimalWithAdmissibleHeuristic()) return false;
        return combo.heuristic() == null || combo.heuristic().properties().claimedAdmissible();
    }

    private static void resetPeakHeap() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) pool.resetPeakUsage();
        }
    }

    private static long peakHeap() {
        long sum = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP && pool.getPeakUsage() != null) sum += pool.getPeakUsage().getUsed();
        }
        return sum;
    }

    private static void message(Listener out, List<String> log, String msg) {
        log.add(Instant.now() + " " + msg);
        out.onMessage(msg);
    }

    private static ThreadFactory namedThreads() {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "npuzzle-bench-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
