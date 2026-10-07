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
import com.example.npuzzleai.search.SearchObserver;
import com.example.npuzzleai.search.SearchResult;
import com.example.npuzzleai.search.SearchStatus;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** BFS tìm kiếm mù - baseline tối ưu về số bước, kiểm tra đích ngay khi sinh node. */
public final class BreadthFirstSearch implements SearchAlgorithm {
    private static final AlgorithmProperties PROPERTIES =
            new AlgorithmProperties(false, true, false, 4, "Tìm kiếm mù");

    @Override
    public String id() {
        return "bfs";
    }

    @Override
    public String displayName() {
        return "BFS";
    }

    @Override
    public String description() {
        return "Tìm kiếm theo chiều rộng: tối ưu về số bước nhưng bộ nhớ tăng theo hàm mũ.";
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
        Goal goal = problem.goal();
        Node root = new Node(problem.start(), null, null, 0);
        if (goal.isGoal(root.board)) return ctx.solved(Paths.movesTo(root));

        ArrayDeque<Node> frontier = new ArrayDeque<>();
        Set<Board> visited = new HashSet<>();
        frontier.add(root);
        visited.add(root.board);

        while (!frontier.isEmpty()) {
            if (ctx.shouldStop()) return ctx.stoppedResult();
            if (ctx.memoryExceeded(visited.size())) return ctx.stoppedResult();

            Node node = frontier.poll();
            ctx.expanded(node.board, node.g, 0, node.g, node.g);
            for (int d = 0; d < Move.COUNT; d++) {
                Move m = Move.byOrdinal(d);
                if (!node.board.canMove(m) || (node.move != null && m == node.move.opposite())) continue;
                Board child = node.board.move(m);
                if (!visited.add(child)) {
                    ctx.metrics().duplicates++;
                    continue;
                }
                Node childNode = new Node(child, node, m, node.g + 1);
                ctx.generated(child, childNode.g, 0, childNode.g, childNode.g);
                if (goal.isGoal(child)) return ctx.solved(Paths.movesTo(childNode));
                frontier.add(childNode);
            }
            if (frontier.size() > ctx.metrics().maxOpen) ctx.metrics().maxOpen = frontier.size();
            ctx.metrics().maxClosed = visited.size();
        }
        return ctx.failed(SearchStatus.NO_SOLUTION, "Không tồn tại lời giải cho trạng thái hiện tại.");
    }

    private static final class Node implements Paths.Step {
        final Board board;
        final Node parent;
        final Move move;
        final int g;

        Node(Board board, Node parent, Move move, int g) {
            this.board = board;
            this.parent = parent;
            this.move = move;
            this.g = g;
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
