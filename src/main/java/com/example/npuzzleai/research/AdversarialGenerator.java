package com.example.npuzzleai.research;

import com.example.npuzzleai.algorithms.AlgorithmRegistry;
import com.example.npuzzleai.algorithms.IDAStarSearch;
import com.example.npuzzleai.benchmark.Dataset;
import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.BoardGenerator;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Move;
import com.example.npuzzleai.core.PuzzleProblem;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.SearchAlgorithm;
import com.example.npuzzleai.search.SearchBudget;
import com.example.npuzzleai.search.SearchObserver;
import com.example.npuzzleai.search.SearchResult;
import com.example.npuzzleai.verify.ExactDistanceTable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Sinh puzzle "đối kháng" bằng tiến hoá (μ+λ): tìm trạng thái làm heuristic hoặc solver gặp trường hợp xấu.
 *
 * <ul>
 *   <li>{@link Objective#EXPANSIONS}: fitness = số node mà solver (mặc định A*) mở rộng, có trần ngân sách.</li>
 *   <li>{@link Objective#HEURISTIC_GAP}: fitness = h*(s) − h(s) - heuristic đánh giá thấp nhất bao nhiêu.
 *       h* lấy từ bảng chính xác (3x3) hoặc IDA* + additive PDB (4x4).</li>
 * </ul>
 * Đột biến giữ tính khả giải: đi ngẫu nhiên 1..k nước, hoặc hai phép đổi chỗ ô số rời nhau (giữ parity).
 * Kết quả xuất được thành {@link Dataset} để benchmark - minh hoạ benchmark trung bình là chưa đủ.
 */
public final class AdversarialGenerator {

    public enum Objective { EXPANSIONS, HEURISTIC_GAP }

    /**
     * @param goal          đích
     * @param objective     mục tiêu
     * @param algorithm     thuật toán đo số node (EXPANSIONS)
     * @param heuristic     heuristic bị "tấn công"
     * @param population    μ - số cá thể giữ lại mỗi thế hệ
     * @param generations   số thế hệ
     * @param maxMutation   số nước đi ngẫu nhiên tối đa mỗi lần đột biến
     * @param nodeBudget    trần số node mở rộng khi đo fitness (EXPANSIONS)
     * @param seed          seed
     */
    public record Config(Goal goal, Objective objective, String algorithm, String heuristic, int population,
                         int generations, int maxMutation, long nodeBudget, long seed) {
        public Config {
            if (population < 2 || generations < 1 || maxMutation < 1) throw new IllegalArgumentException("Tham số tiến hoá không hợp lệ");
            if (objective == Objective.HEURISTIC_GAP && goal.size() > 4) {
                throw new IllegalArgumentException("HEURISTIC_GAP cần h* - chỉ hỗ trợ tới 4x4");
            }
        }
    }

    /** Một cá thể đã đánh giá. */
    public record Candidate(Board board, double fitness, int heuristicValue, int optimalLength, long expanded) {
    }

    /** Nhận tiến độ sau mỗi thế hệ. */
    public interface Listener {
        void onGeneration(int generation, Candidate best, double meanFitness);
    }

    private final Config config;
    private final Heuristic heuristic;
    private final SearchAlgorithm algorithm;
    private final Map<Board, Candidate> cache = new HashMap<>();
    private ExactDistanceTable exact;
    private Heuristic oracleHeuristic;
    private long evaluations;

    public AdversarialGenerator(Config config) {
        this.config = config;
        this.heuristic = HeuristicRegistry.defaults().create(config.heuristic());
        this.algorithm = AlgorithmRegistry.defaults().create(config.algorithm());
        if (!heuristic.supports(config.goal().size())) {
            throw new IllegalArgumentException("Heuristic " + config.heuristic() + " không hỗ trợ kích thước này");
        }
    }

    public long evaluations() {
        return evaluations;
    }

    /** Chạy tiến hoá; trả về quần thể cuối sắp theo fitness giảm dần. */
    public List<Candidate> run(Listener listener, BooleanSupplier cancelled) {
        Goal goal = config.goal();
        heuristic.prepare(goal);
        if (config.objective() == Objective.HEURISTIC_GAP) {
            if (goal.size() <= 3) {
                exact = ExactDistanceTable.forGoal(goal);
            } else {
                oracleHeuristic = HeuristicRegistry.defaults().create("apdb");
                oracleHeuristic.prepare(goal);
            }
        }
        Random rnd = new Random(config.seed());
        Map<Board, Candidate> population = new LinkedHashMap<>();
        while (population.size() < config.population()) {
            Board b = goal.size() <= 3 ? BoardGenerator.uniformSolvable(goal, rnd)
                    : BoardGenerator.randomWalk(goal, 30 + rnd.nextInt(30), rnd);
            population.putIfAbsent(b, evaluate(b));
        }
        List<Candidate> ranked = sorted(population.values());
        for (int gen = 1; gen <= config.generations(); gen++) {
            if (cancelled != null && cancelled.getAsBoolean()) break;
            List<Candidate> offspring = new ArrayList<>();
            for (int i = 0; i < config.population(); i++) {
                Candidate parent = tournament(ranked, rnd);
                Board child = mutate(parent.board(), rnd);
                if (!population.containsKey(child)) offspring.add(evaluate(child));
            }
            for (Candidate c : offspring) population.putIfAbsent(c.board(), c);
            ranked = sorted(population.values());
            if (ranked.size() > config.population()) ranked = new ArrayList<>(ranked.subList(0, config.population()));
            population.clear();
            for (Candidate c : ranked) population.put(c.board(), c);
            if (listener != null) {
                double mean = ranked.stream().mapToDouble(Candidate::fitness).average().orElse(0);
                listener.onGeneration(gen, ranked.get(0), mean);
            }
        }
        return ranked;
    }

    /** Đóng gói kết quả thành dataset để benchmark (kèm độ dài tối ưu nếu biết). */
    public static Dataset toDataset(String name, Goal goal, List<Candidate> candidates, long seed) {
        List<Board> boards = new ArrayList<>();
        int[] optimal = new int[candidates.size()];
        boolean known = true;
        for (int i = 0; i < candidates.size(); i++) {
            boards.add(candidates.get(i).board());
            optimal[i] = candidates.get(i).optimalLength();
            if (optimal[i] < 0) known = false;
        }
        return new Dataset(name, "Puzzle đối kháng sinh bằng tiến hoá", goal, seed, boards, known ? optimal : null);
    }

    Candidate evaluate(Board b) {
        Candidate cached = cache.get(b);
        if (cached != null) return cached;
        evaluations++;
        Goal goal = config.goal();
        int h = heuristic.estimate(b, goal);
        Candidate c;
        if (config.objective() == Objective.HEURISTIC_GAP) {
            int hStar = optimalLength(b);
            c = new Candidate(b, hStar < 0 ? -1 : hStar - h, h, hStar, 0);
        } else {
            SearchResult r = algorithm.solve(new PuzzleProblem(b, goal), heuristic,
                    SearchBudget.unlimited().withMaxExpanded(config.nodeBudget()).withTimeout(60_000), SearchObserver.NONE);
            int len = r.solved() && algorithm.properties().optimalWithAdmissibleHeuristic()
                    && heuristic.properties().claimedAdmissible() ? r.length() : -1;
            if (len < 0 && goal.size() <= 3) len = ExactDistanceTable.forGoal(goal).distance(b);
            c = new Candidate(b, r.metrics().expanded, h, len, r.metrics().expanded);
        }
        cache.put(b, c);
        return c;
    }

    private int optimalLength(Board b) {
        if (exact != null) return exact.distance(b);
        SearchResult r = new IDAStarSearch(0).solve(new PuzzleProblem(b, config.goal()), oracleHeuristic,
                SearchBudget.ofTimeout(20_000), SearchObserver.NONE);
        return r.solved() ? r.length() : -1;
    }

    private static List<Candidate> sorted(Iterable<Candidate> items) {
        List<Candidate> list = new ArrayList<>();
        for (Candidate c : items) list.add(c);
        list.sort(Comparator.comparingDouble(Candidate::fitness).reversed());
        return list;
    }

    private static Candidate tournament(List<Candidate> ranked, Random rnd) {
        Candidate best = null;
        for (int i = 0; i < 3; i++) {
            Candidate c = ranked.get(rnd.nextInt(ranked.size()));
            if (best == null || c.fitness() > best.fitness()) best = c;
        }
        return best;
    }

    /** Đột biến giữ khả giải: đi ngẫu nhiên, hoặc hai phép đổi chỗ ô số rời nhau (parity không đổi). */
    Board mutate(Board b, Random rnd) {
        if (rnd.nextBoolean() || b.cellCount() - 1 < 4) {
            int steps = 1 + rnd.nextInt(config.maxMutation());
            Board cur = b;
            Move prev = null;
            for (int s = 0; s < steps; s++) {
                List<Move> moves = new ArrayList<>(cur.legalMoves());
                if (prev != null && moves.size() > 1) moves.remove(prev.opposite());
                Move m = moves.get(rnd.nextInt(moves.size()));
                cur = cur.move(m);
                prev = m;
            }
            return cur;
        }
        int[] v = b.toArray();
        List<Integer> tilesIdx = new ArrayList<>();
        for (int i = 0; i < v.length; i++) if (v[i] != 0) tilesIdx.add(i);
        java.util.Collections.shuffle(tilesIdx, rnd);
        swap(v, tilesIdx.get(0), tilesIdx.get(1));
        swap(v, tilesIdx.get(2), tilesIdx.get(3));
        return Board.of(b.size(), v);
    }

    private static void swap(int[] v, int a, int b) {
        int t = v[a];
        v[a] = v[b];
        v[b] = t;
    }
}
