package com.example.npuzzleai.research;

import com.example.npuzzleai.benchmark.Dataset;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Solvability;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.verify.ExactDistanceTable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdversarialGeneratorTest {

    private static AdversarialGenerator.Config gapConfig(long seed, int generations) {
        return new AdversarialGenerator.Config(Goal.standard(3), AdversarialGenerator.Objective.HEURISTIC_GAP,
                "astar", "manhattan", 20, generations, 6, 100_000, seed);
    }

    @Test
    void heuristicGapImprovesAndIsCorrect() {
        List<Double> bestPerGeneration = new ArrayList<>();
        AdversarialGenerator gen = new AdversarialGenerator(gapConfig(5L, 15));
        List<AdversarialGenerator.Candidate> result = gen.run((g, best, mean) -> bestPerGeneration.add(best.fitness()), null);
        for (int i = 1; i < bestPerGeneration.size(); i++) {
            assertTrue(bestPerGeneration.get(i) >= bestPerGeneration.get(i - 1), "(μ+λ) không được làm giảm fitness tốt nhất");
        }
        Goal goal = Goal.standard(3);
        ExactDistanceTable exact = ExactDistanceTable.forGoal(goal);
        Heuristic md = HeuristicRegistry.defaults().create("manhattan");
        for (AdversarialGenerator.Candidate c : result) {
            assertTrue(Solvability.isSolvable(c.board(), goal), "Đột biến phải giữ khả giải");
            int hStar = exact.distance(c.board());
            assertEquals(hStar, c.optimalLength());
            assertEquals(hStar - md.estimate(c.board(), goal), c.fitness(), 1e-9);
        }
        // So với trung bình toàn không gian (h* − Manhattan ≈ 8), cá thể tốt nhất phải khó hơn rõ rệt.
        assertTrue(result.get(0).fitness() >= 12, "Tiến hoá phải tìm được ca Manhattan đánh giá thấp đáng kể");
    }

    @Test
    void sameSeedSameResult() {
        List<AdversarialGenerator.Candidate> a = new AdversarialGenerator(gapConfig(9L, 5)).run(null, null);
        List<AdversarialGenerator.Candidate> b = new AdversarialGenerator(gapConfig(9L, 5)).run(null, null);
        assertEquals(a.get(0).board(), b.get(0).board());
        assertEquals(a.size(), b.size());
    }

    @Test
    void expansionsObjectiveAndDatasetExport() {
        AdversarialGenerator.Config cfg = new AdversarialGenerator.Config(Goal.standard(3),
                AdversarialGenerator.Objective.EXPANSIONS, "astar", "misplaced", 10, 4, 4, 5_000, 3L);
        List<AdversarialGenerator.Candidate> result = new AdversarialGenerator(cfg).run(null, null);
        assertTrue(result.get(0).fitness() > 0);
        assertTrue(result.get(0).fitness() <= 5_000, "Fitness bị chặn bởi trần ngân sách node");
        Dataset ds = AdversarialGenerator.toDataset("adv", Goal.standard(3), result, 3L);
        assertEquals(result.size(), ds.instances().size());
        assertNotNull(ds.optimalLength(), "3x3 phải có độ dài tối ưu đi kèm");
    }
}
