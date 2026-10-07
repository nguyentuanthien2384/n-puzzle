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
 * RBFS - Recursive Best-First Search (Korf 1993).
 *
 * <p>Mô phỏng hành vi best-first trong không gian tuyến tính: mỗi lời gọi nhận f-limit là giá trị
 * của "phương án thay thế tốt nhất" ở các mức trên. Khi mọi con vượt f-limit, hàm quay lui và
 * trả về f nhỏ nhất của các con - giá trị này được <i>backup</i> lên node cha để lần quay lại sau
 * không phải tìm từ đầu. Tối ưu khi h chấp nhận được.</p>
 *
 * <p>Instrumentation riêng: recursiveCalls, maxDepth (độ sâu đệ quy), fLimitUpdates (số lần giá trị
 * backup thay đổi f của một con) và regenerated (số lần quay lại mở rộng một nhánh đã từng mở rộng).</p>
 */
public final class RecursiveBestFirstSearch implements SearchAlgorithm {
    private static final int INF = Integer.MAX_VALUE;
    private static final int FOUND = -1;
    private static final AlgorithmProperties PROPERTIES =
            new AlgorithmProperties(true, true, true, 5, "Heuristic tối ưu, bộ nhớ tuyến tính");

    @Override
    public String id() {
        return "rbfs";
    }

    @Override
    public String displayName() {
        return "RBFS";
    }

    @Override
    public String description() {
        return "Best-first đệ quy với f-limit và giá trị backup (Korf 1993): bộ nhớ tuyến tính.";
    }

    @Override
    public AlgorithmProperties properties() {
        return PROPERTIES;
    }

    @Override
    public SearchResult solve(PuzzleProblem problem, Heuristic heuristic, SearchBudget budget, SearchObserver observer) {
        SearchResult early = SearchContext.precheck(this, problem, heuristic);
        if (early != null) return early;
        SearchContext ctx = new SearchContext(this, problem, heuristic, budget, observer);
        return new Run(ctx, problem.goal()).execute();
    }

    private static final class Run {
        private final SearchContext ctx;
        private final SearchMetrics metrics;
        private final Goal goal;
        private Move[] stack = new Move[128];
        private int solutionLength;

        Run(SearchContext ctx, Goal goal) {
            this.ctx = ctx;
            this.metrics = ctx.metrics();
            this.goal = goal;
        }

        SearchResult execute() {
            Board start = ctx.start();
            int h0 = ctx.h(start);
            int r = rbfs(start, 0, h0, h0, null, INF);
            if (r == FOUND) return ctx.solved(Paths.fromStack(stack, solutionLength));
            if (ctx.stopped()) return ctx.stoppedResult();
            return ctx.failed(SearchStatus.NO_SOLUTION, "Không tồn tại lời giải cho trạng thái hiện tại.");
        }

        /**
         * @param storedF giá trị F hiện tại của node (có thể đã được backup, lớn hơn g + h)
         * @param fLimit  giá trị phương án thay thế tốt nhất ở các mức trên
         * @return FOUND, hoặc giá trị F mới để backup lên cha
         */
        private int rbfs(Board board, int g, int h, int storedF, Move previous, int fLimit) {
            metrics.recursiveCalls++;
            if (goal.isGoal(board)) {
                solutionLength = g;
                return FOUND;
            }
            if (ctx.shouldStop()) return INF;
            ctx.expanded(board, g, h, storedF, g);
            if (g + 1 > metrics.maxOpen) metrics.maxOpen = g + 1;
            if (g >= stack.length) {
                Move[] bigger = new Move[stack.length * 2];
                System.arraycopy(stack, 0, bigger, 0, stack.length);
                stack = bigger;
            }

            Board[] children = new Board[3];
            Move[] moves = new Move[3];
            int[] hs = new int[3];
            int[] fs = new int[3];
            boolean[] entered = new boolean[3];
            int count = 0;
            int staticF = g + h;
            for (int d = 0; d < Move.COUNT; d++) {
                Move m = Move.byOrdinal(d);
                if (!board.canMove(m) || (previous != null && m == previous.opposite())) continue;
                Board child = board.move(m);
                int hc = ctx.h(child);
                int fc = g + 1 + hc;
                // Nếu node đã được backup (F > f tĩnh), các con kế thừa F làm cận dưới (Korf 1993).
                if (staticF < storedF) fc = Math.max(storedF, fc);
                children[count] = child;
                moves[count] = m;
                hs[count] = hc;
                fs[count] = fc;
                count++;
                ctx.generated(child, g + 1, hc, fc, g + 1);
            }
            if (count == 0) return INF;

            while (true) {
                int best = 0;
                for (int i = 1; i < count; i++) if (fs[i] < fs[best]) best = i;
                if (fs[best] > fLimit || fs[best] == INF) return fs[best];
                int alternative = INF;
                for (int i = 0; i < count; i++) if (i != best && fs[i] < alternative) alternative = fs[i];

                if (entered[best]) metrics.regenerated++;
                entered[best] = true;
                stack[g] = moves[best];
                int result = rbfs(children[best], g + 1, hs[best], fs[best], moves[best],
                        Math.min(fLimit, alternative));
                if (result == FOUND) return FOUND;
                if (ctx.stopped()) return INF;
                if (result != fs[best]) metrics.fLimitUpdates++;
                fs[best] = result;
            }
        }
    }
}
