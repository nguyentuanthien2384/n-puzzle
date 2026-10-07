package com.example.npuzzleai.algorithms;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.BoardGenerator;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Move;
import com.example.npuzzleai.core.PuzzleProblem;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.SearchBudget;
import com.example.npuzzleai.search.SearchObserver;
import com.example.npuzzleai.search.SearchResult;
import com.example.npuzzleai.search.SearchStatus;
import com.example.npuzzleai.verify.ExactDistanceTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** HDA* (song song, hash-distributed) và MCTS/UCT (baseline Monte-Carlo). */
class ParallelAndMonteCarloTest {
    private static final SearchBudget BUDGET = SearchBudget.ofTimeout(30_000);

    @ParameterizedTest(name = "HDA* {0} worker")
    @ValueSource(ints = {1, 2, 4, 8})
    void hdaIsOptimalForAnyWorkerCount(int workers) {
        HashDistributedAStar hda = new HashDistributedAStar(workers);
        Heuristic md = HeuristicRegistry.defaults().create("manhattan");
        for (PuzzleProblem p : OptimalityRegressionTest.golden3x3()) {
            SearchResult r = hda.solve(p, md, BUDGET, SearchObserver.NONE);
            assertEquals(SearchStatus.SOLVED, r.status(), r.message());
            assertTrue(r.pathReachesGoal());
            assertEquals(ExactDistanceTable.forGoal(p.goal()).distance(p.start()), r.length(),
                    "HDA* " + workers + " worker phải tối ưu tại " + p.start());
            assertEquals(workers, r.metrics().workers);
        }
    }

    @Test
    void hdaAgreesWithAStarOn4x4AndExchangesMessages() {
        Goal goal = Goal.standard(4);
        Heuristic apdb = HeuristicRegistry.defaults().create("apdb");
        Random rnd = new Random(77);
        for (int i = 0; i < 3; i++) {
            PuzzleProblem p = new PuzzleProblem(BoardGenerator.randomWalk(goal, 60, rnd), goal);
            SearchResult reference = AStarSearch.standard().solve(p, apdb, BUDGET, SearchObserver.NONE);
            SearchResult r = new HashDistributedAStar(4).solve(p, apdb, BUDGET, SearchObserver.NONE);
            assertTrue(r.pathReachesGoal());
            assertEquals(reference.length(), r.length());
            if (reference.length() > 3) assertTrue(r.metrics().messages > 0, "4 worker phải trao đổi trạng thái");
            assertTrue(r.metrics().loadImbalancePct >= 100);
            assertTrue(r.metrics().iterations > 0, "Phải ghi số vòng đồng bộ");
        }
    }

    @Test
    void hdaHonoursCancellationAndUnsolvable() {
        Goal goal = Goal.standard(4);
        PuzzleProblem hard = new PuzzleProblem(BoardGenerator.uniformSolvable(goal, new Random(1)), goal);
        Heuristic zero = HeuristicRegistry.defaults().create("zero");
        SearchResult cancelled = new HashDistributedAStar(2).solve(hard, zero, SearchBudget.unlimited(),
                SearchObserver.cancellable(() -> true));
        assertEquals(SearchStatus.CANCELLED, cancelled.status());
        SearchResult limited = new HashDistributedAStar(2).solve(hard, zero,
                SearchBudget.unlimited().withMaxExpanded(2_000), SearchObserver.NONE);
        assertEquals(SearchStatus.NODE_LIMIT, limited.status());

        PuzzleProblem unsolvable = new PuzzleProblem(BoardGenerator.swapTwoTiles(goal.board()), goal);
        assertEquals(SearchStatus.UNSOLVABLE, new HashDistributedAStar(2).solve(unsolvable, zero, BUDGET,
                SearchObserver.NONE).status());
    }

    @Test
    void mctsFindsValidButNotNecessarilyOptimalPaths() {
        Goal goal = Goal.blankFirst(3);
        ExactDistanceTable exact = ExactDistanceTable.forGoal(goal);
        Random rnd = new Random(9);
        MonteCarloTreeSearch mcts = new MonteCarloTreeSearch(200);
        assertFalse(mcts.properties().optimalWithAdmissibleHeuristic());
        for (int i = 0; i < 8; i++) {
            Board start = BoardGenerator.randomWalk(goal, 10 + rnd.nextInt(20), rnd);
            SearchResult r = mcts.solve(new PuzzleProblem(start, goal), HeuristicRegistry.defaults().create("manhattan"),
                    BUDGET, SearchObserver.NONE);
            assertEquals(SearchStatus.SOLVED, r.status(), r.message());
            assertTrue(r.pathReachesGoal(), "Đường đi MCTS phải hợp lệ");
            assertTrue(r.length() >= exact.distance(start));
            assertTrue(r.metrics().iterations > 0, "Phải ghi số mô phỏng");
            List<Board> path = r.path();
            assertEquals(path.size(), path.stream().distinct().count(), "Đường đi đã được cắt vòng lặp");
        }
    }

    @Test
    void mctsIsDeterministicAndSolves4x4() {
        Goal goal = Goal.standard(4);
        Board start = BoardGenerator.randomWalk(goal, 30, new Random(4));
        PuzzleProblem p = new PuzzleProblem(start, goal);
        Heuristic lc = HeuristicRegistry.defaults().create("linear-conflict");
        SearchResult a = new MonteCarloTreeSearch(150).solve(p, lc, BUDGET, SearchObserver.NONE);
        SearchResult b = new MonteCarloTreeSearch(150).solve(p, lc, BUDGET, SearchObserver.NONE);
        assertTrue(a.pathReachesGoal());
        assertEquals(a.moves(), b.moves(), "Cùng trạng thái và số mô phỏng phải cho cùng kết quả");
    }

    @Test
    void cycleRemovalKeepsAValidShorterPath() {
        Board start = Goal.blankFirst(3).board();
        // "RDUL" đi rồi quay lại đúng start; theo sau là đoạn thật "RRDD".
        List<Move> backAndForth = Move.parseSequence("RDULRRDD");
        assertEquals(Move.parseSequence("RRDD"), MonteCarloTreeSearch.removeCycles(start, backAndForth));
        // Ô trống đi quanh khối 2x2 phải đủ 3 vòng (12 nước) mới về trạng thái cũ.
        List<Move> twelveCycle = Move.parseSequence("RDLURDLURDLUDD");
        List<Move> cleaned = MonteCarloTreeSearch.removeCycles(start, twelveCycle);
        assertEquals(Move.parseSequence("DD"), cleaned);
        assertEquals(start.apply(twelveCycle), start.apply(cleaned));
    }
}
