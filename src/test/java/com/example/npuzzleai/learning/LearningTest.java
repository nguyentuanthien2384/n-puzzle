package com.example.npuzzleai.learning;

import com.example.npuzzleai.algorithms.AStarSearch;
import com.example.npuzzleai.algorithms.AlgorithmRegistry;
import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.BoardGenerator;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.PuzzleProblem;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.SearchAlgorithm;
import com.example.npuzzleai.search.SearchBudget;
import com.example.npuzzleai.search.SearchObserver;
import com.example.npuzzleai.search.SearchResult;
import com.example.npuzzleai.verify.ExactDistanceTable;
import com.example.npuzzleai.verify.HeuristicReport;
import com.example.npuzzleai.verify.HeuristicVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Learned heuristic (MLP) và Focal Search: học được nhưng cận độ dài vẫn được bảo đảm. */
class LearningTest {
    private static final SearchBudget BUDGET = SearchBudget.ofTimeout(30_000);

    @Test
    void exactTrainingBeatsManhattanAccuracy() {
        Goal goal = Goal.standard(3);
        HeuristicTrainer.Report r = HeuristicTrainer.trainExact(goal, HeuristicTrainer.Options.defaults());
        HeuristicReport manhattan = HeuristicVerifier.verify(HeuristicRegistry.defaults().create("manhattan"), goal);
        assertTrue(r.validationMae() < manhattan.meanError(),
                "Mạng nơ-ron (MAE " + r.validationMae() + ") phải chính xác hơn Manhattan (sai số TB " + manhattan.meanError() + ")");
        assertTrue(r.validationPairs().length > 0);
        assertEquals(3, r.model().size());
    }

    @Test
    void modelFileRoundTrip(@TempDir Path dir) throws IOException {
        Goal goal = Goal.standard(3);
        HeuristicTrainer.Options quick = new HeuristicTrainer.Options(8, 3, 64, 0.01, 5_000, 1L);
        LearnedModel model = HeuristicTrainer.trainExact(goal, quick).model();
        Path file = dir.resolve("m.model");
        model.save(file);
        LearnedModel loaded = LearnedModel.load(file);
        Random rnd = new Random(2);
        for (int i = 0; i < 200; i++) {
            Board b = BoardGenerator.uniformSolvable(goal, rnd);
            assertEquals(model.predict(b, goal), loaded.predict(b, goal), 1e-6);
        }
        Heuristic h = HeuristicRegistry.defaults().create("learned:" + file);
        assertEquals(0, h.estimate(goal.board(), goal));
        assertTrue(h.supports(3));
        assertFalse(h.supports(4), "Mô hình 3x3 không dùng cho 4x4");
    }

    @Test
    void learnedHeuristicIsDeclaredExperimental() {
        Heuristic h = HeuristicRegistry.defaults().create("learned");
        assertFalse(h.properties().claimedAdmissible(), "Heuristic học được không được khai báo admissible");
        assertTrue(h.properties().experimental());
        Goal goal = Goal.blankFirst(3);
        assertEquals(0, h.estimate(goal.board(), goal));
        Board oneMove = goal.board().move(goal.board().legalMoves().get(0));
        assertTrue(h.estimate(oneMove, goal) >= 1, "Không phải đích thì h >= 1");
    }

    @Test
    void focalSearchRespectsSuboptimalityBound() {
        Heuristic md = HeuristicRegistry.defaults().create("manhattan");
        Random rnd = new Random(31);
        for (double w : new double[]{1.0, 1.5, 2.0}) {
            SearchAlgorithm focal = AlgorithmRegistry.defaults().create("focal:" + w);
            assertEquals(w == 1.0, focal.properties().optimalWithAdmissibleHeuristic());
            for (Goal goal : new Goal[]{Goal.standard(3), Goal.blankFirst(3)}) {
                ExactDistanceTable exact = ExactDistanceTable.forGoal(goal);
                for (int i = 0; i < 8; i++) {
                    Board start = BoardGenerator.uniformSolvable(goal, rnd);
                    SearchResult r = focal.solve(new PuzzleProblem(start, goal), md, BUDGET, SearchObserver.NONE);
                    assertTrue(r.pathReachesGoal(), r.message());
                    int opt = exact.distance(start);
                    assertTrue(r.length() <= w * opt + 1e-9, "Focal w=" + w + " cho " + r.length() + " > w × " + opt);
                    if (w == 1.0) assertEquals(opt, r.length());
                }
            }
        }
    }

    @Test
    void focalSearchWithExplicitOrderingOn4x4() {
        Goal goal = Goal.standard(4);
        Heuristic apdb = HeuristicRegistry.defaults().create("apdb");
        SearchAlgorithm focal = AlgorithmRegistry.defaults().create("focal:2:linear-conflict");
        assertEquals("focal:2:linear-conflict", focal.id());
        Random rnd = new Random(8);
        for (int i = 0; i < 3; i++) {
            PuzzleProblem p = new PuzzleProblem(BoardGenerator.randomWalk(goal, 60, rnd), goal);
            int optimal = AStarSearch.standard().solve(p, apdb, BUDGET, SearchObserver.NONE).length();
            SearchResult r = focal.solve(p, apdb, BUDGET, SearchObserver.NONE);
            assertTrue(r.pathReachesGoal());
            assertTrue(r.length() <= 2 * optimal);
        }
    }

    @Test
    void solverLabelledTrainingOn4x4() {
        HeuristicTrainer.Options quick = new HeuristicTrainer.Options(8, 5, 32, 0.01, 10_000, 3L);
        HeuristicTrainer.Report r = HeuristicTrainer.trainFromSolver(Goal.standard(4), 12, 10, 30,
                FeatureExtractor.FeatureSet.BASIC_PDB, quick, null);
        assertEquals(4, r.model().size());
        assertTrue(r.trainSamples() > 50, "Mỗi trạng thái trên đường đi tối ưu là một mẫu có nhãn");
        assertTrue(Double.isFinite(r.validationRmse()));
    }
}
