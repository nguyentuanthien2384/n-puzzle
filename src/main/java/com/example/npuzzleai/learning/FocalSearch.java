package com.example.npuzzleai.learning;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Move;
import com.example.npuzzleai.core.PuzzleProblem;
import com.example.npuzzleai.search.AlgorithmProperties;
import com.example.npuzzleai.search.ConfigurableAlgorithm;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.SearchAlgorithm;
import com.example.npuzzleai.search.SearchBudget;
import com.example.npuzzleai.search.SearchContext;
import com.example.npuzzleai.search.SearchMetrics;
import com.example.npuzzleai.search.SearchObserver;
import com.example.npuzzleai.search.SearchResult;
import com.example.npuzzleai.search.SearchStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Focal Search (A*<sub>ε</sub>, Pearl &amp; Kim 1982) - tìm kiếm bounded-suboptimal kết hợp heuristic học được.
 *
 * <p>OPEN sắp theo f = g + h với h là heuristic <i>chấp nhận được</i> được truyền vào (ví dụ PDB).
 * FOCAL = {n ∈ OPEN : f(n) ≤ w · f_min} sắp theo heuristic thứ cấp h<sub>F</sub> - mặc định là mạng nơ-ron
 * ({@link LearnedHeuristic}). Node mở rộng luôn lấy từ FOCAL nên lời giải có độ dài ≤ w × tối ưu, bất kể
 * h<sub>F</sub> chính xác hay không: cận dưới vẫn do heuristic đã kiểm định đảm nhiệm, mạng nơ-ron chỉ
 * quyết định thứ tự. Đây là hướng "admissible h = PDB, learned h = ordering signal" an toàn về correctness.</p>
 *
 * <p>Mã: {@code focal:w} (h<sub>F</sub> = learned) hoặc {@code focal:w:mã_heuristic} (h<sub>F</sub> tuỳ chọn,
 * ví dụ {@code focal:1.5:linear-conflict}). Nếu h<sub>F</sub> không hỗ trợ kích thước bảng, dùng chính h.</p>
 */
public final class FocalSearch implements ConfigurableAlgorithm {
    public static final double DEFAULT_WEIGHT = 1.5;

    private final double weight;
    private final Heuristic focalHeuristic;
    private final String focalId;
    private final AlgorithmProperties properties;

    /** Dùng cho ServiceLoader: focal:1.5 với heuristic học được. */
    public FocalSearch() {
        this(DEFAULT_WEIGHT, null, "learned");
    }

    public FocalSearch(double weight, Heuristic focalHeuristic, String focalId) {
        if (weight < 1) throw new IllegalArgumentException("Trọng số Focal Search phải >= 1");
        this.weight = weight;
        this.focalHeuristic = focalHeuristic;
        this.focalId = focalId;
        this.properties = new AlgorithmProperties(true, weight == 1.0, false, 5, "Bounded-suboptimal (focal)");
    }

    @Override
    public SearchAlgorithm configure(String parameter) {
        String[] parts = parameter.split(":", 2);
        double w = Double.parseDouble(parts[0]);
        if (parts.length == 1) return new FocalSearch(w, null, "learned");
        String hId = parts[1].trim();
        return new FocalSearch(w, null, hId);
    }

    @Override
    public String id() {
        String w = String.format(Locale.ROOT, "%.2f", weight).replaceAll("0+$", "").replaceAll("\\.$", "");
        return "focal:" + w + (focalId.equals("learned") ? "" : ":" + focalId);
    }

    @Override
    public String displayName() {
        return "Focal Search (w=" + weight + ", thứ tự theo " + focalId + ")";
    }

    @Override
    public String description() {
        return "OPEN theo f = g + h (admissible), chọn trong FOCAL (f ≤ w·f_min) theo " + focalId
                + ": độ dài ≤ w × tối ưu kể cả khi " + focalId + " không chấp nhận được.";
    }

    @Override
    public AlgorithmProperties properties() {
        return properties;
    }

    private Heuristic resolveFocal() {
        if (focalHeuristic != null) return focalHeuristic;
        if (focalId.equals("learned")) return new LearnedHeuristic();
        return com.example.npuzzleai.heuristics.HeuristicRegistry.defaults().create(focalId);
    }

    @Override
    public SearchResult solve(PuzzleProblem problem, Heuristic heuristic, SearchBudget budget, SearchObserver observer) {
        SearchResult early = SearchContext.precheck(this, problem, heuristic);
        if (early != null) return early;
        Heuristic focal = resolveFocal();
        if (!focal.supports(problem.size())) focal = heuristic;
        focal.prepare(problem.goal()); // huấn luyện/nạp mô hình trước khi bắt đầu đo thời gian
        SearchContext ctx = new SearchContext(this, problem, heuristic, budget, observer);
        return new Run(ctx, problem.goal(), focal, weight).execute();
    }

    private static final class Run {
        private final SearchContext ctx;
        private final SearchMetrics metrics;
        private final Goal goal;
        private final Heuristic focal;
        private final double weight;
        private final TreeSet<Node> open = new TreeSet<>(Comparator.comparingInt((Node n) -> n.f).thenComparingLong(n -> n.seq));
        private final TreeSet<Node> focalSet = new TreeSet<>(Comparator.comparingInt((Node n) -> n.hf)
                .thenComparingInt(n -> n.f).thenComparingLong(n -> n.seq));
        private final Map<Board, Node> best = new HashMap<>();
        private int focalBound = -1;
        private long seq;
        private long closedCount;

        Run(SearchContext ctx, Goal goal, Heuristic focal, double weight) {
            this.ctx = ctx;
            this.metrics = ctx.metrics();
            this.goal = goal;
            this.focal = focal;
            this.weight = weight;
        }

        SearchResult execute() {
            Board start = ctx.start();
            insert(new Node(start, null, null, 0, ctx.h(start), focal.estimate(start, goal), seq++));
            while (!open.isEmpty()) {
                if (ctx.shouldStop()) return ctx.stoppedResult();
                if (ctx.memoryExceeded(open.size() + closedCount)) return ctx.stoppedResult();
                syncFocal();
                Node node = focalSet.pollFirst();
                open.remove(node);
                if (goal.isGoal(node.board)) return ctx.solved(movesTo(node));
                node.closed = true;
                closedCount++;
                ctx.expanded(node.board, node.g, node.h, node.f, node.g);
                for (int d = 0; d < Move.COUNT; d++) {
                    Move m = Move.byOrdinal(d);
                    if (!node.board.canMove(m) || (node.move != null && m == node.move.opposite())) continue;
                    Board child = node.board.move(m);
                    int g = node.g + 1;
                    Node existing = best.get(child);
                    int h, hf;
                    if (existing != null) {
                        if (g >= existing.g) {
                            metrics.duplicates++;
                            continue;
                        }
                        if (existing.closed) {
                            metrics.reopened++;
                            closedCount--;
                        } else {
                            open.remove(existing);
                            focalSet.remove(existing);
                        }
                        h = existing.h;
                        hf = existing.hf;
                    } else {
                        h = ctx.h(child);
                        hf = focal.estimate(child, goal);
                    }
                    Node c = new Node(child, node, m, g, h, hf, seq++);
                    insert(c);
                    ctx.generated(child, g, h, c.f, g);
                }
                if (open.size() > metrics.maxOpen) metrics.maxOpen = open.size();
                if (closedCount > metrics.maxClosed) metrics.maxClosed = closedCount;
            }
            return ctx.failed(SearchStatus.NO_SOLUTION, "Không tồn tại lời giải cho trạng thái hiện tại.");
        }

        private void insert(Node n) {
            best.put(n.board, n);
            open.add(n);
            if (n.f <= focalBound) focalSet.add(n);
        }

        /** Giữ bất biến FOCAL = {n ∈ OPEN : f(n) ≤ ⌊w · f_min⌋} khi f_min thay đổi. */
        private void syncFocal() {
            int fmin = open.first().f;
            int bound = (int) Math.floor(weight * fmin + 1e-9);
            if (bound > focalBound) {
                for (Node n : open.subSet(probe(focalBound + 1, Long.MIN_VALUE), true, probe(bound, Long.MAX_VALUE), true)) {
                    focalSet.add(n);
                }
            } else if (bound < focalBound) {
                // f_min có thể giảm khi h không nhất quán: loại các node vượt cận mới khỏi FOCAL.
                for (Node n : open.subSet(probe(bound + 1, Long.MIN_VALUE), true, probe(focalBound, Long.MAX_VALUE), true)) {
                    focalSet.remove(n);
                }
            }
            focalBound = bound;
        }

        private static Node probe(int f, long seq) {
            Node p = new Node(null, null, null, 0, 0, 0, seq);
            p.f = f;
            return p;
        }

        private static List<Move> movesTo(Node node) {
            List<Move> moves = new ArrayList<>();
            for (Node n = node; n != null && n.move != null; n = n.parent) moves.add(n.move);
            Collections.reverse(moves);
            return moves;
        }
    }

    private static final class Node {
        final Board board;
        final Node parent;
        final Move move;
        final int g;
        final int h;
        final int hf;
        final long seq;
        int f;
        boolean closed;

        Node(Board board, Node parent, Move move, int g, int h, int hf, long seq) {
            this.board = board;
            this.parent = parent;
            this.move = move;
            this.g = g;
            this.h = h;
            this.hf = hf;
            this.seq = seq;
            this.f = g + h;
        }
    }
}
