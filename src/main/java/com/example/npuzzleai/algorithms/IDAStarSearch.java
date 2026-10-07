package com.example.npuzzleai.algorithms;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Move;
import com.example.npuzzleai.core.PuzzleProblem;
import com.example.npuzzleai.search.AlgorithmProperties;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.SearchAlgorithm;
import com.example.npuzzleai.search.SearchBudget;
import com.example.npuzzleai.search.SearchContext;
import com.example.npuzzleai.search.SearchMetrics;
import com.example.npuzzleai.search.SearchObserver;
import com.example.npuzzleai.search.SearchResult;
import com.example.npuzzleai.search.SearchStatus;

/**
 * IDA* - Iterative Deepening A* (Korf 1985), có tuỳ chọn bảng chuyển vị (IDA*-TT).
 *
 * <p>Mỗi vòng là một DFS giới hạn f = g + h &lt;= ngưỡng; ngưỡng kế tiếp là f nhỏ nhất bị cắt.
 * Bộ nhớ chỉ tỉ lệ với độ sâu lời giải, đánh đổi bằng việc sinh lại node qua các vòng -
 * {@code regenerated} = số node mở rộng ở các vòng trước vòng cuối.</p>
 *
 * <p>IDA*-TT thêm bảng chuyển vị ánh xạ trực tiếp (direct-mapped) kích thước cố định theo MB:
 * nếu một trạng thái đã được thăm trong <i>cùng vòng</i> với g nhỏ hơn hoặc bằng, cây con
 * của nó không thể cho lời giải mới hay ngưỡng nhỏ hơn nên được cắt an toàn. Mỗi entry ghi
 * số hiệu vòng để không phải xoá bảng giữa các vòng. Khoá là bảng nén chính xác (tới 5x5)
 * nên không có va chạm giả gây mất tối ưu.</p>
 */
public final class IDAStarSearch implements SearchAlgorithm {
    private static final int INF = Integer.MAX_VALUE;
    private static final int FOUND = -1;
    private static final int ENTRY_BYTES = 8 + 8 + 4 + 4;

    private final int ttMegabytes;
    private final AlgorithmProperties properties;

    /** @param ttMegabytes dung lượng bảng chuyển vị; 0 = IDA* cơ bản */
    public IDAStarSearch(int ttMegabytes) {
        if (ttMegabytes < 0) throw new IllegalArgumentException("Dung lượng TT không được âm");
        this.ttMegabytes = ttMegabytes;
        this.properties = new AlgorithmProperties(true, true, ttMegabytes == 0, 5, "Heuristic tối ưu, bộ nhớ tuyến tính");
    }

    @Override
    public String id() {
        return ttMegabytes == 0 ? "ida" : "ida-tt:" + ttMegabytes;
    }

    @Override
    public String displayName() {
        return ttMegabytes == 0 ? "IDA*" : "IDA*-TT (" + ttMegabytes + " MB)";
    }

    @Override
    public String description() {
        return ttMegabytes == 0
                ? "Sâu lặp theo ngưỡng f = g + h (Korf 1985): bộ nhớ theo độ sâu, đổi lại phải sinh lại node."
                : "IDA* + bảng chuyển vị " + ttMegabytes + " MB để giảm node sinh lại do trùng trạng thái.";
    }

    @Override
    public AlgorithmProperties properties() {
        return properties;
    }

    @Override
    public SearchResult solve(PuzzleProblem problem, Heuristic heuristic, SearchBudget budget, SearchObserver observer) {
        SearchResult early = SearchContext.precheck(this, problem, heuristic);
        if (early != null) return early;
        SearchContext ctx = new SearchContext(this, problem, heuristic, budget, observer);
        TranspositionTable tt = ttMegabytes > 0 && problem.start().hasCompactKey()
                ? new TranspositionTable(ttMegabytes) : null;
        return new Run(ctx, problem.goal(), tt).execute();
    }

    /** Trạng thái của một lần giải - tách riêng để instance thuật toán dùng song song được. */
    private static final class Run {
        private final SearchContext ctx;
        private final SearchMetrics metrics;
        private final Goal goal;
        private final TranspositionTable tt;
        private Move[] stack = new Move[128];
        private int solutionLength;
        private int bound;
        private int iteration;
        private long iterationExpanded;

        Run(SearchContext ctx, Goal goal, TranspositionTable tt) {
            this.ctx = ctx;
            this.metrics = ctx.metrics();
            this.goal = goal;
            this.tt = tt;
        }

        SearchResult execute() {
            Board start = ctx.start();
            int h0 = ctx.h(start);
            bound = h0;
            while (true) {
                iteration++;
                metrics.iterations = iteration;
                long expandedBefore = metrics.expanded;
                int t = dfs(start, 0, h0, null);
                iterationExpanded = metrics.expanded - expandedBefore;
                if (t == FOUND) {
                    metrics.regenerated = metrics.expanded - iterationExpanded;
                    return ctx.solved(Paths.fromStack(stack, solutionLength));
                }
                if (ctx.stopped()) {
                    metrics.regenerated = metrics.expanded - iterationExpanded;
                    return ctx.stoppedResult();
                }
                if (t == INF) return ctx.failed(SearchStatus.NO_SOLUTION, "Không tồn tại lời giải cho trạng thái hiện tại.");
                bound = t;
                metrics.fLimitUpdates++;
            }
        }

        /** DFS giới hạn bởi bound; trả FOUND, hoặc f nhỏ nhất vượt ngưỡng (INF nếu bị dừng/cắt). */
        private int dfs(Board board, int g, int h, Move previous) {
            int f = g + h;
            if (f > bound) {
                ctx.pruned(board, g, h, f, g);
                return f;
            }
            if (goal.isGoal(board)) {
                solutionLength = g;
                return FOUND;
            }
            if (ctx.shouldStop()) return INF;
            if (tt != null && tt.seenWithBetterOrEqualG(board, g, iteration)) {
                metrics.duplicates++;
                return INF;
            }
            ctx.expanded(board, g, h, f, g);
            if (g + 1 > metrics.maxOpen) metrics.maxOpen = g + 1;
            if (g >= stack.length) {
                Move[] bigger = new Move[stack.length * 2];
                System.arraycopy(stack, 0, bigger, 0, stack.length);
                stack = bigger;
            }

            int min = INF;
            for (int d = 0; d < Move.COUNT; d++) {
                Move m = Move.byOrdinal(d);
                if (!board.canMove(m) || (previous != null && m == previous.opposite())) continue;
                Board child = board.move(m);
                int hc = ctx.h(child);
                ctx.generated(child, g + 1, hc, g + 1 + hc, g + 1);
                stack[g] = m;
                int t = dfs(child, g + 1, hc, m);
                if (t == FOUND) return FOUND;
                if (ctx.stopped()) return INF;
                if (t < min) min = t;
            }
            return min;
        }
    }

    /**
     * Bảng chuyển vị ánh xạ trực tiếp: mỗi khoá chỉ có một ô, ghi đè khi va chạm (luôn an toàn -
     * chỉ làm mất khả năng cắt, không cắt sai). Mỗi entry lưu số vòng để vô hiệu hoá entry cũ.
     */
    private static final class TranspositionTable {
        private final long[] keyLow;
        private final long[] keyHigh;
        private final int[] gValue;
        private final int[] iterationStamp;
        private final int mask;

        TranspositionTable(int megabytes) {
            long entries = (long) megabytes * 1024 * 1024 / ENTRY_BYTES;
            int capacity = Integer.highestOneBit((int) Math.min(entries, 1 << 28));
            capacity = Math.max(capacity, 1024);
            keyLow = new long[capacity];
            keyHigh = new long[capacity];
            gValue = new int[capacity];
            iterationStamp = new int[capacity];
            mask = capacity - 1;
        }

        boolean seenWithBetterOrEqualG(Board board, int g, int iteration) {
            long lo = board.keyLow();
            long hi = board.keyHigh();
            long mix = lo * 0x9E3779B97F4A7C15L ^ (hi + 0x632BE59BD9B4E019L) * 0xC2B2AE3D27D4EB4FL;
            int i = (int) (mix ^ (mix >>> 29)) & mask;
            if (iterationStamp[i] == iteration && keyLow[i] == lo && keyHigh[i] == hi) {
                if (gValue[i] <= g) return true;
                gValue[i] = g;
                return false;
            }
            keyLow[i] = lo;
            keyHigh[i] = hi;
            gValue[i] = g;
            iterationStamp[i] = iteration;
            return false;
        }
    }
}
