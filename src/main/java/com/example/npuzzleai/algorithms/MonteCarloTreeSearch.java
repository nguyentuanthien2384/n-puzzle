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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * MCTS/UCT (Kocsis &amp; Szepesvári 2006) theo kiểu receding horizon - baseline để <b>so sánh paradigm</b>,
 * không phải solver tối ưu.
 *
 * <p>Tại mỗi trạng thái đang đứng, chạy {@code simulations} lần: chọn nhánh bằng UCB1, mở một con mới,
 * rollout ε-greedy theo heuristic (phần thưởng 1 nếu tới đích, 1/(1+h) nếu không), lan truyền ngược.
 * Sau đó thực hiện nước đi được thăm nhiều nhất. Nếu một rollout chạm đích, nối thẳng đường đi đó.
 * Cuối cùng cắt bỏ các vòng lặp trạng thái trong đường đi thực hiện.</p>
 *
 * <p>N-Puzzle cổ điển là bài toán đường đi ngắn nhất tất định với cận dưới mạnh, nên A*, IDA* và PDB
 * tận dụng thông tin tốt hơn; MCTS ở đây để minh hoạ sự khác biệt (độ dài lời giải, số mô phỏng).
 * Kết quả tất định: seed suy ra từ trạng thái xuất phát và số mô phỏng.</p>
 */
public final class MonteCarloTreeSearch implements SearchAlgorithm {
    public static final int DEFAULT_SIMULATIONS = 300;
    private static final double EXPLORATION = 0.7;
    private static final double EPSILON = 0.15;

    private final int simulations;
    private static final AlgorithmProperties PROPERTIES =
            new AlgorithmProperties(true, false, false, 4, "Monte-Carlo (không tối ưu)");

    public MonteCarloTreeSearch(int simulations) {
        if (simulations < 1) throw new IllegalArgumentException("Số mô phỏng phải >= 1");
        this.simulations = simulations;
    }

    @Override
    public String id() {
        return "mcts:" + simulations;
    }

    @Override
    public String displayName() {
        return "MCTS/UCT (" + simulations + " mô phỏng/nước)";
    }

    @Override
    public String description() {
        return "UCB1 + rollout ε-greedy theo heuristic, chọn nước được thăm nhiều nhất: baseline so sánh paradigm, không tối ưu.";
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
        return new Run(ctx, problem.goal(), simulations, new Random(problem.start().hashCode() * 31L + simulations)).execute();
    }

    private static final class TreeNode {
        final Board board;
        final TreeNode parent;
        final Move move;
        final int depth;
        final List<Move> untried;
        final List<TreeNode> children = new ArrayList<>(4);
        int visits;
        double value;

        TreeNode(Board board, TreeNode parent, Move move, Move previous) {
            this.board = board;
            this.parent = parent;
            this.move = move;
            this.depth = parent == null ? 0 : parent.depth + 1;
            this.untried = new ArrayList<>(4);
            for (Move m : board.legalMoves()) if (previous == null || m != previous.opposite()) untried.add(m);
        }

        TreeNode bestUcb() {
            TreeNode best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            double logN = Math.log(Math.max(1, visits));
            for (TreeNode c : children) {
                double score = c.visits == 0 ? Double.POSITIVE_INFINITY
                        : c.value / c.visits + EXPLORATION * Math.sqrt(logN / c.visits);
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            return best;
        }
    }

    private static final class Run {
        private final SearchContext ctx;
        private final SearchMetrics metrics;
        private final Goal goal;
        private final int simulations;
        private final Random rnd;
        private final int rolloutDepth;
        private final int maxSteps;

        Run(SearchContext ctx, Goal goal, int simulations, Random rnd) {
            this.ctx = ctx;
            this.metrics = ctx.metrics();
            this.goal = goal;
            this.simulations = simulations;
            this.rnd = rnd;
            int cells = goal.size() * goal.size();
            this.rolloutDepth = 4 * cells;
            this.maxSteps = 60 * cells;
        }

        SearchResult execute() {
            Board current = ctx.start();
            List<Move> executed = new ArrayList<>();
            Map<Board, Integer> visitedOnPath = new HashMap<>();
            visitedOnPath.put(current, 1);
            Move previous = null;
            while (!goal.isGoal(current)) {
                if (executed.size() >= maxSteps) {
                    return ctx.failed(SearchStatus.FAILED, "MCTS không tới đích sau " + maxSteps + " nước đi.");
                }
                TreeNode root = new TreeNode(current, null, null, previous);
                List<Move> bestRollout = null;
                for (int s = 0; s < simulations; s++) {
                    if (ctx.shouldStopNow()) return ctx.stoppedResult();
                    metrics.iterations++;
                    TreeNode node = root;
                    while (node.untried.isEmpty() && !node.children.isEmpty() && !goal.isGoal(node.board)) {
                        node = node.bestUcb();
                    }
                    if (!goal.isGoal(node.board) && !node.untried.isEmpty()) {
                        Move m = node.untried.remove(rnd.nextInt(node.untried.size()));
                        TreeNode child = new TreeNode(node.board.move(m), node, m, m);
                        node.children.add(child);
                        node = child;
                        ctx.expanded(node.board, node.depth, 0, node.depth, node.depth);
                    }
                    List<Move> rolloutMoves = new ArrayList<>();
                    double reward = rollout(node, rolloutMoves);
                    if (reward >= 1.0) {
                        List<Move> full = treeMoves(node);
                        full.addAll(rolloutMoves);
                        if (bestRollout == null || full.size() < bestRollout.size()) bestRollout = full;
                    }
                    for (TreeNode t = node; t != null; t = t.parent) {
                        t.visits++;
                        t.value += reward;
                    }
                }
                if (bestRollout != null) {
                    executed.addAll(bestRollout);
                    break;
                }
                TreeNode chosen = chooseMove(root, visitedOnPath);
                executed.add(chosen.move);
                current = chosen.board;
                previous = chosen.move;
                visitedOnPath.merge(current, 1, Integer::sum);
            }
            return ctx.solved(removeCycles(ctx.start(), executed));
        }

        /** Ưu tiên con được thăm nhiều nhất mà chưa nằm trên đường đã đi (tránh dao động qua lại). */
        private TreeNode chooseMove(TreeNode root, Map<Board, Integer> visitedOnPath) {
            TreeNode best = null;
            double bestKey = Double.NEGATIVE_INFINITY;
            for (TreeNode c : root.children) {
                double key = c.visits - 1_000_000.0 * visitedOnPath.getOrDefault(c.board, 0);
                if (key > bestKey) {
                    bestKey = key;
                    best = c;
                }
            }
            if (best == null) { // không có con nào (không xảy ra với bảng hợp lệ) - đi ngẫu nhiên
                Move m = root.untried.get(rnd.nextInt(root.untried.size()));
                best = new TreeNode(root.board.move(m), root, m, m);
            }
            return best;
        }

        /** Rollout ε-greedy theo h; trả 1 nếu tới đích, ngược lại 1/(1+h cuối). */
        private double rollout(TreeNode from, List<Move> moves) {
            Board b = from.board;
            Move previous = from.move;
            if (goal.isGoal(b)) return 1.0;
            Move[] candidates = new Move[Move.COUNT];
            for (int step = 0; step < rolloutDepth; step++) {
                int count = 0;
                for (int d = 0; d < Move.COUNT; d++) {
                    Move m = Move.byOrdinal(d);
                    if (b.canMove(m) && (previous == null || m != previous.opposite())) candidates[count++] = m;
                }
                Move chosen;
                if (rnd.nextDouble() < EPSILON) {
                    chosen = candidates[rnd.nextInt(count)];
                } else {
                    chosen = null;
                    int bestH = Integer.MAX_VALUE;
                    int ties = 0;
                    for (int i = 0; i < count; i++) {
                        int h = ctx.h(b.move(candidates[i]));
                        if (h < bestH) {
                            bestH = h;
                            chosen = candidates[i];
                            ties = 1;
                        } else if (h == bestH && rnd.nextInt(++ties) == 0) {
                            chosen = candidates[i];
                        }
                    }
                }
                b = b.move(chosen);
                previous = chosen;
                moves.add(chosen);
                metrics.generated++;
                if (goal.isGoal(b)) return 1.0;
            }
            return 1.0 / (2.0 + ctx.h(b));
        }

        private static List<Move> treeMoves(TreeNode node) {
            List<Move> moves = new ArrayList<>();
            for (TreeNode t = node; t != null && t.move != null; t = t.parent) moves.add(0, t.move);
            return moves;
        }
    }

    /** Cắt vòng lặp: nếu một trạng thái xuất hiện lại, bỏ đoạn đi vòng giữa hai lần xuất hiện. */
    static List<Move> removeCycles(Board start, List<Move> moves) {
        List<Board> states = new ArrayList<>();
        List<Move> out = new ArrayList<>();
        Map<Board, Integer> index = new HashMap<>();
        states.add(start);
        index.put(start, 0);
        Board b = start;
        for (Move m : moves) {
            b = b.move(m);
            Integer seen = index.get(b);
            if (seen != null) {
                for (int i = states.size() - 1; i > seen; i--) index.remove(states.remove(i));
                while (out.size() > seen) out.remove(out.size() - 1);
            } else {
                states.add(b);
                out.add(m);
                index.put(b, states.size() - 1);
            }
        }
        return out;
    }
}
