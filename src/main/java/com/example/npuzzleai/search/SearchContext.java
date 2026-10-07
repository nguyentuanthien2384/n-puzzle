package com.example.npuzzleai.search;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Move;
import com.example.npuzzleai.core.PuzzleProblem;
import com.example.npuzzleai.core.Solvability;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

/**
 * Hạ tầng dùng chung cho mọi thuật toán: đếm metrics, gọi heuristic, kiểm tra ngân sách,
 * hủy, phát sự kiện theo {@link TraceMode} và đóng gói {@link SearchResult}.
 *
 * <p>Gom các việc này vào một chỗ đảm bảo mọi thuật toán được đo theo cùng một định nghĩa
 * (expanded/generated/...), điều kiện cần để so sánh công bằng trong benchmark.</p>
 *
 * <p>Một context chỉ dùng cho một lần giải, trên một luồng.</p>
 */
public final class SearchContext {
    private static final int CHECK_MASK = 0xFF;
    private static final long PROGRESS_INTERVAL = 4096;
    private static final int TIMING_MASK = 0x1F;
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private final String algorithmId;
    private final String heuristicId;
    private final PuzzleProblem problem;
    private final Goal goal;
    private final Heuristic heuristic;
    private final SearchBudget budget;
    private final SearchObserver observer;
    private final boolean observing;
    private final TraceMode traceMode;
    private final long sampleInterval;
    private final SearchMetrics metrics = new SearchMetrics();
    private final long startNanos;
    private final long deadlineNanos;
    private final long startCpuNanos;
    private final long bytesPerNode;
    private SearchStatus stopStatus;
    private long checks;

    public SearchContext(SearchAlgorithm algorithm, PuzzleProblem problem, Heuristic heuristic,
                         SearchBudget budget, SearchObserver observer) {
        this.algorithmId = algorithm.id();
        this.heuristic = algorithm.properties().usesHeuristic() ? heuristic : null;
        this.heuristicId = this.heuristic == null ? "none" : this.heuristic.id();
        this.problem = problem;
        this.goal = problem.goal();
        this.budget = budget == null ? SearchBudget.DEFAULT : budget;
        this.observer = observer == null ? SearchObserver.NONE : observer;
        this.observing = this.observer != SearchObserver.NONE;
        this.traceMode = this.observer.traceMode();
        this.sampleInterval = Math.max(1, this.observer.sampleInterval());
        this.bytesPerNode = estimateBytesPerNode(problem.size());
        this.startCpuNanos = cpuNow();
        this.startNanos = System.nanoTime();
        this.deadlineNanos = this.budget.timeoutMillis() > 0
                ? startNanos + this.budget.timeoutMillis() * 1_000_000L : 0L;
    }

    /**
     * Kiểm tra chung trước khi tìm: kích thước được hỗ trợ, heuristic phù hợp, trạng thái khả giải.
     * Trả về kết quả kết thúc sớm, hoặc null nếu được phép tìm kiếm.
     */
    public static SearchResult precheck(SearchAlgorithm algorithm, PuzzleProblem problem, Heuristic heuristic) {
        int size = problem.size();
        boolean usesHeuristic = algorithm.properties().usesHeuristic();
        String hId = usesHeuristic && heuristic != null ? heuristic.id() : "none";
        if (!algorithm.supports(size)) {
            return early(algorithm.id(), hId, problem, SearchStatus.UNSUPPORTED,
                    algorithm.displayName() + " không hỗ trợ bảng " + size + "x" + size + ".");
        }
        if (usesHeuristic && heuristic == null) {
            return early(algorithm.id(), hId, problem, SearchStatus.UNSUPPORTED,
                    algorithm.displayName() + " cần một heuristic.");
        }
        if (usesHeuristic && !heuristic.supports(size)) {
            return early(algorithm.id(), hId, problem, SearchStatus.UNSUPPORTED,
                    "Heuristic " + heuristic.displayName() + " không hỗ trợ bảng " + size + "x" + size + ".");
        }
        if (!Solvability.isSolvable(problem.start(), problem.goal())) {
            return early(algorithm.id(), hId, problem, SearchStatus.UNSOLVABLE,
                    "Trạng thái hiện tại không thể biến đổi về trạng thái đích đã chọn.");
        }
        return null;
    }

    private static SearchResult early(String algorithmId, String heuristicId, PuzzleProblem problem,
                                      SearchStatus status, String message) {
        return new SearchResult(algorithmId, heuristicId, status, problem.start(), problem.goal(),
                List.of(), new SearchMetrics(), message);
    }

    /**
     * Ước lượng thô số byte cho một node đang giữ trong bộ nhớ (node + Board + mảng ô +
     * entry HashMap/PriorityQueue). Chỉ dùng để áp ngân sách bộ nhớ tương đối giữa các thuật toán.
     */
    public static long estimateBytesPerNode(int size) {
        int cells = size * size;
        long boardBytes = 32 + 16 + ((cells + 7) / 8) * 8;
        return 64 + boardBytes + 48;
    }

    public SearchMetrics metrics() {
        return metrics;
    }

    public Goal goal() {
        return goal;
    }

    public Board start() {
        return problem.start();
    }

    public SearchBudget budget() {
        return budget;
    }

    public long bytesPerNode() {
        return bytesPerNode;
    }

    /** Giá trị heuristic, có đếm số lần gọi và đo mẫu thời gian. Trả 0 nếu thuật toán không dùng heuristic. */
    public int h(Board board) {
        if (heuristic == null) return 0;
        long calls = ++metrics.heuristicCalls;
        if ((calls & TIMING_MASK) != 0) return heuristic.estimate(board, goal);
        long t0 = System.nanoTime();
        int v = heuristic.estimate(board, goal);
        metrics.heuristicTimeNs += (System.nanoTime() - t0) * (TIMING_MASK + 1);
        return v;
    }

    /** Gọi mỗi lần mở rộng node. Trả true nếu phải dừng (hủy, hết giờ, vượt giới hạn node). */
    public boolean shouldStop() {
        if (stopStatus != null) return true;
        long maxExpanded = budget.maxExpandedNodes();
        if (maxExpanded > 0 && metrics.expanded >= maxExpanded) {
            stopStatus = SearchStatus.NODE_LIMIT;
            return true;
        }
        if ((++checks & CHECK_MASK) != 0) return false;
        if (observer.isCancelled() || Thread.currentThread().isInterrupted()) {
            stopStatus = SearchStatus.CANCELLED;
            return true;
        }
        if (deadlineNanos != 0 && System.nanoTime() > deadlineNanos) {
            stopStatus = SearchStatus.TIMEOUT;
            return true;
        }
        return false;
    }

    /** Kiểm tra ngân sách bộ nhớ ước lượng theo số node đang giữ. */
    public boolean memoryExceeded(long nodesInMemory) {
        long max = budget.maxMemoryBytes();
        if (max > 0 && nodesInMemory * bytesPerNode > max) {
            stopStatus = SearchStatus.MEMORY_LIMIT;
            return true;
        }
        return false;
    }

    public boolean stopped() {
        return stopStatus != null;
    }

    public void expanded(Board board, int g, int h, double f, int depth) {
        long count = ++metrics.expanded;
        if (depth > metrics.maxDepth) metrics.maxDepth = depth;
        if (!observing) return;
        if (traceMode == TraceMode.FULL_TRACE
                || (traceMode == TraceMode.SAMPLED && (count == 1 || count % sampleInterval == 0))) {
            observer.onExpand(new SearchEvent(SearchEvent.Type.EXPAND, board, g, h, f, depth, count));
        }
        if (count % PROGRESS_INTERVAL == 0) observer.onProgress(metrics);
    }

    public void generated(Board board, int g, int h, double f, int depth) {
        metrics.generated++;
        if (traceMode == TraceMode.FULL_TRACE && observing) {
            observer.onGenerate(new SearchEvent(SearchEvent.Type.GENERATE, board, g, h, f, depth, metrics.expanded));
        }
    }

    public void pruned(Board board, int g, int h, double f, int depth) {
        metrics.pruned++;
        if (traceMode == TraceMode.FULL_TRACE && observing) {
            observer.onPrune(new SearchEvent(SearchEvent.Type.PRUNE, board, g, h, f, depth, metrics.expanded));
        }
    }

    public SearchResult solved(List<Move> moves) {
        finishTimes();
        metrics.solutionLength = moves.size();
        metrics.solutionCost = moves.size();
        if (observing) {
            Board end = problem.start().apply(moves);
            observer.onGoal(new SearchEvent(SearchEvent.Type.GOAL, end, moves.size(), 0, moves.size(),
                    moves.size(), metrics.expanded));
            observer.onProgress(metrics);
        }
        return new SearchResult(algorithmId, heuristicId, SearchStatus.SOLVED, problem.start(), goal,
                moves, metrics, "Tìm thấy lời giải " + moves.size() + " bước.");
    }

    /** Kết quả khi đã dừng do {@link #shouldStop()} hoặc {@link #memoryExceeded}. */
    public SearchResult stoppedResult() {
        SearchStatus status = stopStatus == null ? SearchStatus.CANCELLED : stopStatus;
        return failed(status, messageFor(status));
    }

    public SearchResult failed(SearchStatus status, String message) {
        finishTimes();
        if (observing) observer.onProgress(metrics);
        return new SearchResult(algorithmId, heuristicId, status, problem.start(), goal, List.of(), metrics, message);
    }

    private String messageFor(SearchStatus status) {
        return switch (status) {
            case TIMEOUT -> "Thuật toán quá tốn thời gian (vượt quá " + budget.timeoutMillis() / 1000.0 + " giây).";
            case NODE_LIMIT -> "Vượt quá giới hạn " + budget.maxExpandedNodes() + " node mở rộng.";
            case MEMORY_LIMIT -> "Vượt quá ngân sách bộ nhớ ước lượng "
                    + (budget.maxMemoryBytes() / (1024 * 1024)) + " MB.";
            case CANCELLED -> "Đã dừng tìm kiếm theo yêu cầu.";
            default -> status.label();
        };
    }

    private void finishTimes() {
        metrics.wallTimeNs = System.nanoTime() - startNanos;
        long cpu = cpuNow();
        metrics.cpuTimeNs = cpu >= 0 && startCpuNanos >= 0 ? cpu - startCpuNanos : -1;
    }

    private static long cpuNow() {
        try {
            return THREADS.isCurrentThreadCpuTimeSupported() ? THREADS.getCurrentThreadCpuTime() : -1;
        } catch (UnsupportedOperationException e) {
            return -1;
        }
    }
}
