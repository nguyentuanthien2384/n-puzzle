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

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Best-first search tổng quát với f(n) = wg*g(n) + wh*h(n).
 *
 * <ul>
 *   <li>A*: wg = 1, wh = 1 - tối ưu khi h chấp nhận được (cần reopen nếu h không nhất quán).</li>
 *   <li>Weighted A*: wg = 1, wh = w &gt; 1 - "Fast solve mode", độ dài lời giải &lt;= w * tối ưu.</li>
 *   <li>Greedy Best-First: wg = 0 - chỉ theo h, nhanh, không đảm bảo tối ưu.</li>
 * </ul>
 *
 * <p>Chính sách reopen và tie-break nằm trong {@link Config} (không hard-code) để thí nghiệm
 * có thể ghi rõ cấu hình. OPEN dùng PriorityQueue với xoá lười: bản ghi cũ được đánh dấu stale.</p>
 */
public final class AStarSearch implements SearchAlgorithm {

    /** Cách phá thế hoà khi hai node có cùng f. */
    public enum TieBreak {
        /** h nhỏ hơn trước (gần đích hơn), sau đó theo thứ tự chèn. */
        LOW_H,
        /** g lớn hơn trước (sâu hơn), sau đó theo thứ tự chèn. */
        HIGH_G,
        /** Chèn trước ra trước. */
        FIFO,
        /** Chèn sau ra trước. */
        LIFO
    }

    public record Config(double gWeight, double hWeight, boolean reopenClosed, TieBreak tieBreak) {
        public Config {
            if (gWeight < 0 || hWeight <= 0) throw new IllegalArgumentException("Trọng số không hợp lệ");
        }

        public boolean greedy() {
            return gWeight == 0;
        }
    }

    private final String id;
    private final String displayName;
    private final Config config;
    private final AlgorithmProperties properties;

    public AStarSearch(String id, String displayName, Config config) {
        this.id = id;
        this.displayName = displayName;
        this.config = config;
        boolean optimal = !config.greedy() && config.gWeight() == 1.0 && config.hWeight() == 1.0;
        String family = config.greedy() ? "Best-first (không tối ưu)"
                : optimal ? "Heuristic tối ưu" : "Bounded-suboptimal";
        this.properties = new AlgorithmProperties(true, optimal, false, 5, family);
    }

    public static AStarSearch standard() {
        return new AStarSearch("astar", "A*", new Config(1, 1, true, TieBreak.LOW_H));
    }

    public static AStarSearch withoutReopening() {
        return new AStarSearch("astar-noreopen", "A* (không reopen)", new Config(1, 1, false, TieBreak.LOW_H));
    }

    public static AStarSearch weighted(double weight) {
        if (weight < 1) throw new IllegalArgumentException("Trọng số Weighted A* phải >= 1");
        String w = String.format(Locale.ROOT, "%.2f", weight).replaceAll("0+$", "").replaceAll("\\.$", "");
        return new AStarSearch("wastar:" + w, "Weighted A* (w=" + w + ")", new Config(1, weight, true, TieBreak.LOW_H));
    }

    public static AStarSearch greedy() {
        return new AStarSearch("greedy", "Greedy Best-First", new Config(0, 1, false, TieBreak.LOW_H));
    }

    public Config config() {
        return config;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String description() {
        if (config.greedy()) return "Chỉ xét h(n): rất nhanh nhưng lời giải thường dài hơn tối ưu.";
        if (properties.optimalWithAdmissibleHeuristic()) {
            return "f = g + h, tối ưu khi h chấp nhận được; reopen=" + config.reopenClosed()
                    + ", tie-break=" + config.tieBreak() + ".";
        }
        return "f = g + " + config.hWeight() + "·h: lời giải dài tối đa " + config.hWeight() + " lần tối ưu.";
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
        SearchMetrics metrics = ctx.metrics();
        Goal goal = problem.goal();

        PriorityQueue<Node> open = new PriorityQueue<>(comparator(config.tieBreak()));
        Map<Board, Node> best = new HashMap<>();
        long sequence = 0;
        long closedCount = 0;

        Node root = new Node(problem.start(), null, null, 0, ctx.h(problem.start()), sequence++);
        root.f = priority(root.g, root.h);
        open.add(root);
        best.put(root.board, root);

        while (!open.isEmpty()) {
            if (ctx.shouldStop()) return ctx.stoppedResult();
            if (ctx.memoryExceeded(open.size() + closedCount)) return ctx.stoppedResult();

            Node node = open.poll();
            if (node.stale) continue;
            if (goal.isGoal(node.board)) return ctx.solved(Paths.movesTo(node));

            node.closed = true;
            closedCount++;
            ctx.expanded(node.board, node.g, node.h, node.f, node.g);

            for (int d = 0; d < Move.COUNT; d++) {
                Move m = Move.byOrdinal(d);
                if (!node.board.canMove(m) || (node.move != null && m == node.move.opposite())) continue;
                Board childBoard = node.board.move(m);
                int g = node.g + 1;
                Node existing = best.get(childBoard);
                int h;
                if (existing != null) {
                    if (config.greedy() || g >= existing.g) {
                        metrics.duplicates++;
                        continue;
                    }
                    if (existing.closed) {
                        if (!config.reopenClosed()) {
                            metrics.duplicates++;
                            continue;
                        }
                        metrics.reopened++;
                        closedCount--;
                    }
                    existing.stale = true;
                    h = existing.h; // cùng trạng thái nên h không đổi - không cần gọi lại heuristic
                } else {
                    h = ctx.h(childBoard);
                }
                Node child = new Node(childBoard, node, m, g, h, sequence++);
                child.f = priority(g, h);
                best.put(childBoard, child);
                open.add(child);
                ctx.generated(childBoard, g, h, child.f, g);
            }

            long liveOpen = best.size() - closedCount;
            if (liveOpen > metrics.maxOpen) metrics.maxOpen = liveOpen;
            if (closedCount > metrics.maxClosed) metrics.maxClosed = closedCount;
        }
        return ctx.failed(SearchStatus.NO_SOLUTION, "Không tồn tại lời giải cho trạng thái hiện tại.");
    }

    private double priority(int g, int h) {
        return config.gWeight() * g + config.hWeight() * h;
    }

    private static Comparator<Node> comparator(TieBreak tieBreak) {
        Comparator<Node> byF = Comparator.comparingDouble(n -> n.f);
        return switch (tieBreak) {
            case LOW_H -> byF.thenComparingInt((Node n) -> n.h).thenComparingLong(n -> n.seq);
            case HIGH_G -> byF.thenComparingInt((Node n) -> -n.g).thenComparingLong(n -> n.seq);
            case FIFO -> byF.thenComparingLong((Node n) -> n.seq);
            case LIFO -> byF.thenComparingLong((Node n) -> -n.seq);
        };
    }

    private static final class Node implements Paths.Step {
        final Board board;
        final Node parent;
        final Move move;
        final int g;
        final int h;
        final long seq;
        double f;
        boolean closed;
        boolean stale;

        Node(Board board, Node parent, Move move, int g, int h, long seq) {
            this.board = board;
            this.parent = parent;
            this.move = move;
            this.g = g;
            this.h = h;
            this.seq = seq;
        }

        @Override
        public Paths.Step parent() {
            return parent;
        }

        @Override
        public Move move() {
            return move;
        }
    }
}
