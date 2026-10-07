package com.example.npuzzleai.algorithms;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.BoardGenerator;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Move;
import com.example.npuzzleai.core.PuzzleProblem;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.SearchAlgorithm;
import com.example.npuzzleai.search.SearchBudget;
import com.example.npuzzleai.search.SearchEvent;
import com.example.npuzzleai.search.SearchObserver;
import com.example.npuzzleai.search.SearchResult;
import com.example.npuzzleai.search.SearchStatus;
import com.example.npuzzleai.search.TraceMode;
import com.example.npuzzleai.verify.ExactDistanceTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tính tối ưu: mọi thuật toán tối ưu × heuristic chấp nhận được phải cho đúng h*
 * trên bộ "golden puzzles" 3x3 (oracle BFS ngược), và thống nhất với nhau trên 4x4.
 * Lỗi tinh vi về duplicate/reopen/f-bound vẫn có thể sinh lời giải nhưng mất tối ưu - test này bắt được.
 */
class OptimalityRegressionTest {
    private static final SearchBudget BUDGET = SearchBudget.ofTimeout(30_000);
    private static final List<String> OPTIMAL_ALGORITHMS =
            List.of("astar", "astar-noreopen", "ida", "ida-tt:8", "rbfs", "sma:200000", "hda:3");
    private static final List<String> ADMISSIBLE_HEURISTICS =
            List.of("manhattan", "linear-conflict", "walking-distance", "apdb");

    /** Bộ golden: đích, 1 nước, và các trạng thái ngẫu nhiên đều cho cả hai kiểu đích. */
    static List<PuzzleProblem> golden3x3() {
        List<PuzzleProblem> list = new ArrayList<>();
        Random rnd = new Random(20261006L);
        for (Goal goal : List.of(Goal.standard(3), Goal.blankFirst(3))) {
            list.add(new PuzzleProblem(goal.board(), goal));
            list.add(new PuzzleProblem(goal.board().move(goal.board().legalMoves().get(0)), goal));
            for (int i = 0; i < 4; i++) list.add(new PuzzleProblem(BoardGenerator.uniformSolvable(goal, rnd), goal));
        }
        list.add(new PuzzleProblem(Board.parse("8 6 7 2 5 4 3 0 1"), Goal.standard(3))); // 31 bước - khó nhất
        return list;
    }

    static Stream<Arguments> optimalCombos() {
        List<Arguments> args = new ArrayList<>();
        for (String a : OPTIMAL_ALGORITHMS) for (String h : ADMISSIBLE_HEURISTICS) args.add(Arguments.of(a, h));
        args.add(Arguments.of("bfs", "zero"));
        return args.stream();
    }

    @ParameterizedTest(name = "{0} + {1}")
    @MethodSource("optimalCombos")
    void optimalOnGolden3x3(String algoSpec, String heuristicId) {
        SearchAlgorithm algo = AlgorithmRegistry.defaults().create(algoSpec);
        Heuristic h = HeuristicRegistry.defaults().create(heuristicId);
        for (PuzzleProblem p : golden3x3()) {
            int exact = ExactDistanceTable.forGoal(p.goal()).distance(p.start());
            SearchResult r = algo.solve(p, h, BUDGET, SearchObserver.NONE);
            assertEquals(SearchStatus.SOLVED, r.status(), algoSpec + " + " + heuristicId + " tại " + p.start() + ": " + r.message());
            assertTrue(r.pathReachesGoal(), "Đường đi phải hợp lệ và tới đích");
            assertEquals(exact, r.length(), algoSpec + " + " + heuristicId + " không tối ưu tại " + p.start());
        }
    }

    @Test
    void weightedAStarRespectsSuboptimalityBound() {
        for (double w : new double[]{1.5, 2.0, 3.0}) {
            SearchAlgorithm algo = AStarSearch.weighted(w);
            assertFalse(algo.properties().optimalWithAdmissibleHeuristic());
            for (PuzzleProblem p : golden3x3()) {
                int exact = ExactDistanceTable.forGoal(p.goal()).distance(p.start());
                SearchResult r = algo.solve(p, HeuristicRegistry.defaults().create("manhattan"), BUDGET, SearchObserver.NONE);
                assertTrue(r.pathReachesGoal());
                assertTrue(r.length() <= w * exact + 1e-9, "W-A* w=" + w + " phải có độ dài <= w * tối ưu");
            }
        }
    }

    @Test
    void greedyFindsValidButPossiblyLongerPaths() {
        SearchAlgorithm greedy = AlgorithmRegistry.defaults().create("greedy");
        for (PuzzleProblem p : golden3x3()) {
            SearchResult r = greedy.solve(p, HeuristicRegistry.defaults().create("manhattan"), BUDGET, SearchObserver.NONE);
            assertTrue(r.pathReachesGoal());
            assertTrue(r.length() >= ExactDistanceTable.forGoal(p.goal()).distance(p.start()));
        }
    }

    @Test
    void optimalAlgorithmsAgreeOn4x4() {
        Goal goal = Goal.standard(4);
        Random rnd = new Random(44);
        Heuristic lc = HeuristicRegistry.defaults().create("linear-conflict");
        for (int i = 0; i < 3; i++) {
            PuzzleProblem p = new PuzzleProblem(BoardGenerator.randomWalk(goal, 30, rnd), goal);
            SearchResult reference = AStarSearch.standard().solve(p, lc, BUDGET, SearchObserver.NONE);
            assertTrue(reference.solved(), reference.message());
            String[][] combos = {{"ida", "apdb"}, {"ida-tt:16", "manhattan"}, {"rbfs", "linear-conflict"},
                    {"sma:200000", "linear-conflict"}, {"astar", "walking-distance"}, {"ida", "wd-lc"}};
            for (String[] c : combos) {
                SearchResult r = AlgorithmRegistry.defaults().create(c[0])
                        .solve(p, HeuristicRegistry.defaults().create(c[1]), BUDGET, SearchObserver.NONE);
                assertTrue(r.pathReachesGoal(), c[0] + " + " + c[1] + ": " + r.message());
                assertEquals(reference.length(), r.length(), c[0] + " + " + c[1] + " phải tối ưu như A* + LC");
            }
        }
    }

    @Test
    void transpositionTableNeverExpandsMore() {
        Goal goal = Goal.standard(4);
        PuzzleProblem p = new PuzzleProblem(BoardGenerator.randomWalk(goal, 40, new Random(8)), goal);
        Heuristic md = HeuristicRegistry.defaults().create("manhattan");
        SearchResult basic = new IDAStarSearch(0).solve(p, md, BUDGET, SearchObserver.NONE);
        SearchResult tt = new IDAStarSearch(16).solve(p, md, BUDGET, SearchObserver.NONE);
        assertTrue(basic.solved() && tt.solved());
        assertEquals(basic.length(), tt.length());
        assertTrue(tt.metrics().expanded <= basic.metrics().expanded, "Bảng chuyển vị chỉ được cắt bớt node");
        assertEquals(basic.metrics().iterations, tt.metrics().iterations, "Chuỗi ngưỡng không được thay đổi");
    }

    @Test
    void smaStarEvictsAndStaysOptimalWithSmallMemory() {
        Goal goal = Goal.standard(3);
        ExactDistanceTable exact = ExactDistanceTable.forGoal(goal);
        Random rnd = new Random(12);
        Board start;
        do {
            start = BoardGenerator.uniformSolvable(goal, rnd);
        } while (exact.distance(start) < 20 || exact.distance(start) > 24);
        SearchResult r = new SMAStarSearch(400).solve(new PuzzleProblem(start, goal),
                HeuristicRegistry.defaults().create("manhattan"), BUDGET, SearchObserver.NONE);
        assertEquals(SearchStatus.SOLVED, r.status(), r.message());
        assertEquals(exact.distance(start), r.length());
        assertTrue(r.metrics().evictions > 0, "Với 400 node SMA* phải loại bỏ node");
        assertTrue(r.metrics().maxOpen <= 400 + 3, "Không vượt giới hạn quá số con của một lần mở rộng");
    }

    @Test
    void smaStarReportsMemoryLimitWhenPathCannotFit() {
        Board start = Board.parse("8 6 7 2 5 4 3 0 1"); // cần 31 bước nhưng chỉ có 10 node
        SearchResult r = new SMAStarSearch(10).solve(new PuzzleProblem(start, Goal.standard(3)),
                HeuristicRegistry.defaults().create("manhattan"), BUDGET, SearchObserver.NONE);
        assertEquals(SearchStatus.MEMORY_LIMIT, r.status());
    }

    @Test
    void unsolvableIsRejectedBeforeSearch() {
        Board start = BoardGenerator.swapTwoTiles(Goal.standard(3).board());
        for (SearchAlgorithm a : AlgorithmRegistry.defaults().all()) {
            SearchResult r = a.solve(new PuzzleProblem(start, Goal.standard(3)),
                    HeuristicRegistry.defaults().create("manhattan"), BUDGET, SearchObserver.NONE);
            assertEquals(SearchStatus.UNSOLVABLE, r.status(), a.id());
            assertEquals(0, r.metrics().expanded, a.id() + " không được mở rộng node nào");
        }
    }

    @Test
    void budgetsAndCancellationAreHonoured() {
        Goal goal = Goal.standard(4);
        PuzzleProblem hard = new PuzzleProblem(BoardGenerator.uniformSolvable(goal, new Random(3)), goal);
        Heuristic zero = HeuristicRegistry.defaults().create("zero");

        SearchResult limited = AStarSearch.standard().solve(hard, zero, SearchBudget.unlimited().withMaxExpanded(500), SearchObserver.NONE);
        assertEquals(SearchStatus.NODE_LIMIT, limited.status());
        assertTrue(limited.metrics().expanded <= 500);

        SearchResult memory = AStarSearch.standard().solve(hard, zero,
                SearchBudget.unlimited().withMaxMemoryBytes(64 * 1024), SearchObserver.NONE);
        assertEquals(SearchStatus.MEMORY_LIMIT, memory.status());

        SearchResult cancelled = new IDAStarSearch(0).solve(hard, zero, SearchBudget.unlimited(),
                SearchObserver.cancellable(() -> true));
        assertEquals(SearchStatus.CANCELLED, cancelled.status());

        SearchResult timeout = new RecursiveBestFirstSearch().solve(hard, zero, SearchBudget.ofTimeout(50), SearchObserver.NONE);
        assertEquals(SearchStatus.TIMEOUT, timeout.status());
    }

    @Test
    void traceModesControlEventVolume() {
        Goal goal = Goal.standard(3);
        PuzzleProblem p = new PuzzleProblem(Board.parse("8 6 7 2 5 4 3 0 1"), goal);
        Heuristic md = HeuristicRegistry.defaults().create("manhattan");
        for (TraceMode mode : TraceMode.values()) {
            AtomicLong expands = new AtomicLong();
            AtomicLong generates = new AtomicLong();
            SearchObserver obs = new SearchObserver() {
                @Override
                public TraceMode traceMode() {
                    return mode;
                }

                @Override
                public int sampleInterval() {
                    return 10;
                }

                @Override
                public void onExpand(SearchEvent e) {
                    expands.incrementAndGet();
                }

                @Override
                public void onGenerate(SearchEvent e) {
                    generates.incrementAndGet();
                }
            };
            SearchResult r = AStarSearch.standard().solve(p, md, BUDGET, obs);
            long expanded = r.metrics().expanded;
            switch (mode) {
                case OFF -> assertEquals(0, expands.get() + generates.get());
                case SAMPLED -> {
                    assertEquals(1 + expanded / 10, expands.get());
                    assertEquals(0, generates.get());
                }
                case FULL_TRACE -> {
                    assertEquals(expanded, expands.get());
                    assertEquals(r.metrics().generated, generates.get());
                }
            }
        }
    }

    @Test
    void registryParsesParameters() {
        assertEquals("wastar:2.5", AlgorithmRegistry.defaults().create("wastar:2.5").id());
        assertEquals("sma:1234", AlgorithmRegistry.defaults().create("sma:1234").id());
        assertEquals("ida-tt:32", AlgorithmRegistry.defaults().create("ida-tt:32").id());
        assertThrows(IllegalArgumentException.class, () -> AlgorithmRegistry.defaults().create("khong-co"));
        assertThrows(IllegalArgumentException.class, () -> AlgorithmRegistry.defaults().create("sma:abc"));
        assertEquals(List.of(Move.RIGHT), Move.parseSequence("R"));
    }
}
