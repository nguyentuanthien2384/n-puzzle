package com.example.npuzzleai.core;

import com.example.npuzzleai.verify.ExactDistanceTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm tra khả giải tổng quát: đối chiếu vét cạn với khả năng tới được bằng BFS (2x2, 3x3)
 * và kiểm tra ngẫu nhiên tới 6x6 với đích tuỳ ý.
 */
class SolvabilityTest {

    @Test
    void matchesBfsReachabilityExhaustively() {
        List<Goal> goals = List.of(
                Goal.standard(2), Goal.blankFirst(2),
                Goal.standard(3), Goal.blankFirst(3),
                Goal.of(Board.parse("4 0 1 3 8 2 6 7 5")) // ô trống ở giữa cạnh, đích tuỳ ý
        );
        for (Goal goal : goals) {
            ExactDistanceTable table = ExactDistanceTable.forGoal(goal);
            int solvable = 0;
            for (int r = 0; r < table.permutationCount(); r++) {
                Board b = table.boardAt(r);
                boolean reachable = table.distanceAt(r) >= 0;
                assertEquals(reachable, Solvability.isSolvable(b, goal), "Sai khả giải tại " + b + " với đích " + goal);
                if (reachable) solvable++;
            }
            assertEquals(table.permutationCount() / 2, solvable, "Đúng một nửa hoán vị khả giải");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6})
    void randomWalkSolvableAndTileSwapUnsolvable(int size) {
        Random rnd = new Random(1000 + size);
        for (int i = 0; i < 200; i++) {
            Goal goal = Goal.of(BoardGenerator.uniformSolvable(Goal.standard(size), rnd)); // đích ngẫu nhiên
            Board walked = BoardGenerator.randomWalk(goal, 1 + rnd.nextInt(80), rnd);
            assertTrue(Solvability.isSolvable(walked, goal), "Đi từ đích phải khả giải");
            assertFalse(Solvability.isSolvable(BoardGenerator.swapTwoTiles(walked), goal), "Đổi chỗ 2 ô phải vô nghiệm");
            assertTrue(Solvability.isSolvable(BoardGenerator.uniformSolvable(goal, rnd), goal));
            assertFalse(Solvability.isSolvable(BoardGenerator.unsolvable(goal, rnd), goal));
        }
    }

    @Test
    void differentSizesAreNeverSolvable() {
        assertFalse(Solvability.isSolvable(Goal.standard(3).board(), Goal.standard(4)));
    }
}
