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
import java.util.TreeSet;

/**
 * SMA* - Simplified Memory-bounded A* (Russell 1992), biến thể mở rộng đầy đủ.
 *
 * <p>Người dùng đặt số node tối đa được giữ trong bộ nhớ (hoặc suy ra từ
 * {@link SearchBudget#maxMemoryBytes()}). Khi đầy, thuật toán loại lá "tệ nhất" (f lớn nhất,
 * nông nhất), ghi f của nó vào cha ({@code forgotten}) để biết cần sinh lại khi cần. Giá trị f
 * được backup lên tổ tiên: f(n) = min f các con (kể cả con đã bị quên).</p>
 *
 * <ul>
 *   <li>Chọn mở rộng node có f nhỏ nhất, sâu nhất; kiểm tra đích khi chọn nên lời giải tối ưu nếu
 *       bộ nhớ đủ chứa đường đi tối ưu (độ sâu &lt; maxNodes).</li>
 *   <li>Mỗi lần mở rộng sinh mọi successor còn thiếu, nên số node có thể vượt tạm thời tối đa 3
 *       trước khi bước loại bỏ đưa về giới hạn.</li>
 *   <li>Node ở độ sâu tối đa (maxNodes - 1) không phải đích nhận f = ∞ vì không thể kéo dài thêm.</li>
 *   <li>Nếu node tốt nhất có f = ∞: bộ nhớ không đủ - trả MEMORY_LIMIT.</li>
 * </ul>
 */
public final class SMAStarSearch implements SearchAlgorithm {
    public static final int DEFAULT_MAX_NODES = 100_000;
    private static final int INF = Integer.MAX_VALUE;
    private static final int NONE = -1;
    private static final int MIN_NODES = 8;
    /** Ước lượng thêm cho node SMA* (mảng con + forgotten + 2 entry TreeSet). */
    private static final long EXTRA_BYTES_PER_NODE = 160;

    private final int maxNodes;
    private static final AlgorithmProperties PROPERTIES =
            new AlgorithmProperties(true, true, false, 5, "Heuristic tối ưu, bộ nhớ giới hạn");

    public SMAStarSearch(int maxNodes) {
        if (maxNodes < MIN_NODES) throw new IllegalArgumentException("SMA* cần ít nhất " + MIN_NODES + " node");
        this.maxNodes = maxNodes;
    }

    public int maxNodes() {
        return maxNodes;
    }

    @Override
    public String id() {
        return "sma:" + maxNodes;
    }

    @Override
    public String displayName() {
        return "SMA* (" + maxNodes + " node)";
    }

    @Override
    public String description() {
        return "A* với giới hạn bộ nhớ tường minh: loại lá tệ nhất khi đầy, backup f lên tổ tiên.";
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
        int limit = maxNodes;
        long memoryBytes = ctx.budget().maxMemoryBytes();
        if (memoryBytes > 0) {
            long byBudget = memoryBytes / (ctx.bytesPerNode() + EXTRA_BYTES_PER_NODE);
            limit = (int) Math.max(MIN_NODES, Math.min(limit, byBudget));
        }
        return new Run(ctx, problem.goal(), limit).execute();
    }

    private static final class Run {
        private final SearchContext ctx;
        private final SearchMetrics metrics;
        private final Goal goal;
        private final int limit;
        private final int maxDepth;
        /** Ứng viên mở rộng: f tăng dần, sâu trước. */
        private final TreeSet<SmaNode> open = new TreeSet<>(Comparator
                .comparingInt((SmaNode n) -> n.f)
                .thenComparingInt(n -> -n.depth)
                .thenComparingLong(n -> n.id));
        /** Ứng viên loại bỏ (lá không phải gốc): f giảm dần, nông trước. */
        private final TreeSet<SmaNode> leaves = new TreeSet<>(Comparator
                .comparingInt((SmaNode n) -> -n.f)
                .thenComparingInt(n -> n.depth)
                .thenComparingLong(n -> n.id));
        private long nextId;
        private int used;

        Run(SearchContext ctx, Goal goal, int limit) {
            this.ctx = ctx;
            this.metrics = ctx.metrics();
            this.goal = goal;
            this.limit = limit;
            this.maxDepth = limit - 1;
        }

        SearchResult execute() {
            Board start = ctx.start();
            int h0 = ctx.h(start);
            SmaNode root = new SmaNode(start, null, null, 0, h0, 0, nextId++);
            root.f = h0;
            used = 1;
            add(open, root);

            while (true) {
                if (ctx.shouldStop()) return ctx.stoppedResult();
                if (open.isEmpty() || open.first().f == INF) {
                    return ctx.failed(SearchStatus.MEMORY_LIMIT, "SMA* không đủ bộ nhớ (" + limit
                            + " node) để chứa đường đi tối ưu.");
                }
                SmaNode node = open.first();
                if (goal.isGoal(node.board)) return ctx.solved(Paths.movesTo(node));

                ctx.expanded(node.board, node.g, node.h, node.f, node.depth);
                expand(node);
                remove(open, node);
                remove(leaves, node);
                backup(node);

                while (used > limit) {
                    if (leaves.isEmpty()) break;
                    evict(leaves.first());
                }
                if (used > metrics.maxOpen) metrics.maxOpen = used;
            }
        }

        /** Sinh mọi successor chưa có trong bộ nhớ (lần đầu, hoặc đã bị quên). */
        private void expand(SmaNode node) {
            for (int d = 0; d < Move.COUNT; d++) {
                Move m = Move.byOrdinal(d);
                if (!node.board.canMove(m) || (node.move != null && m == node.move.opposite())) continue;
                if (node.children[d] != null) continue;
                Board childBoard = node.board.move(m);
                int g = node.g + 1;
                int h = ctx.h(childBoard);
                SmaNode child = new SmaNode(childBoard, node, m, g, h, node.depth + 1, nextId++);
                if (child.depth >= maxDepth && !goal.isGoal(childBoard)) {
                    child.f = INF;
                } else {
                    child.f = Math.max(node.f, g + h);
                    if (node.forgotten[d] != NONE) {
                        child.f = Math.max(child.f, node.forgotten[d]);
                        metrics.regenerated++;
                    }
                }
                node.forgotten[d] = NONE;
                node.children[d] = child;
                node.childCount++;
                used++;
                add(open, child);
                add(leaves, child);
                ctx.generated(childBoard, g, h, child.f, child.depth);
            }
        }

        /** Cập nhật f(n) = min f các con (trong bộ nhớ + đã quên), lan truyền lên tổ tiên nếu đổi. */
        private void backup(SmaNode node) {
            SmaNode current = node;
            while (current != null) {
                int newF = INF;
                for (int d = 0; d < Move.COUNT; d++) {
                    SmaNode c = current.children[d];
                    if (c != null && c.f < newF) newF = c.f;
                    if (current.forgotten[d] != NONE && current.forgotten[d] < newF) newF = current.forgotten[d];
                }
                if (current.childCount == 0 && !hasForgotten(current)) return; // chưa từng mở rộng
                if (newF <= current.f) return; // f chỉ tăng trong SMA* (con luôn có f >= cha)
                setF(current, newF);
                metrics.fLimitUpdates++;
                current = current.parent;
            }
        }

        private void evict(SmaNode victim) {
            SmaNode parent = victim.parent;
            remove(open, victim);
            remove(leaves, victim);
            int d = victim.move.ordinal();
            parent.children[d] = null;
            parent.childCount--;
            parent.forgotten[d] = victim.f;
            used--;
            metrics.evictions++;
            if (!parent.inOpen) add(open, parent);
            if (parent.childCount == 0 && parent.parent != null) add(leaves, parent);
        }

        private static boolean hasForgotten(SmaNode n) {
            for (int v : n.forgotten) if (v != NONE) return true;
            return false;
        }

        private void setF(SmaNode n, int f) {
            boolean wasOpen = n.inOpen;
            boolean wasLeaf = n.inLeaves;
            if (wasOpen) remove(open, n);
            if (wasLeaf) remove(leaves, n);
            n.f = f;
            if (wasOpen) add(open, n);
            if (wasLeaf) add(leaves, n);
        }

        private void add(TreeSet<SmaNode> set, SmaNode n) {
            if (set.add(n)) {
                if (set == open) n.inOpen = true;
                else n.inLeaves = true;
            }
        }

        private void remove(TreeSet<SmaNode> set, SmaNode n) {
            if (set == open) {
                if (n.inOpen) {
                    set.remove(n);
                    n.inOpen = false;
                }
            } else if (n.inLeaves) {
                set.remove(n);
                n.inLeaves = false;
            }
        }
    }

    private static final class SmaNode implements Paths.Step {
        final Board board;
        final SmaNode parent;
        final Move move;
        final int g;
        final int h;
        final int depth;
        final long id;
        int f;
        final SmaNode[] children = new SmaNode[Move.COUNT];
        final int[] forgotten = {NONE, NONE, NONE, NONE};
        int childCount;
        boolean inOpen;
        boolean inLeaves;

        SmaNode(Board board, SmaNode parent, Move move, int g, int h, int depth, long id) {
            this.board = board;
            this.parent = parent;
            this.move = move;
            this.g = g;
            this.h = h;
            this.depth = depth;
            this.id = id;
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
