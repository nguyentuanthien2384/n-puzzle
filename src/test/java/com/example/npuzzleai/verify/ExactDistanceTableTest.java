package com.example.npuzzleai.verify;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Move;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Oracle h* phải khớp các hằng số đã biết của 2x2/3x3 trước khi dùng để kiểm định thứ khác. */
class ExactDistanceTableTest {

    @Test
    void knownStateSpaceFacts() {
        ExactDistanceTable t3 = ExactDistanceTable.forGoal(Goal.standard(3));
        assertEquals(362_880, t3.permutationCount());
        assertEquals(181_440, t3.reachableCount(), "8-puzzle có 9!/2 trạng thái tới được");
        assertEquals(31, t3.maxDistance(), "Đường kính 8-puzzle (ô trống đích ở góc) là 31");

        ExactDistanceTable t3b = ExactDistanceTable.forGoal(Goal.blankFirst(3));
        assertEquals(31, t3b.maxDistance());

        ExactDistanceTable t2 = ExactDistanceTable.forGoal(Goal.standard(2));
        assertEquals(12, t2.reachableCount());
        assertEquals(6, t2.maxDistance());
    }

    @Test
    void histogramSumsToReachable() {
        ExactDistanceTable t = ExactDistanceTable.forGoal(Goal.standard(3));
        long sum = 0;
        for (long c : t.histogram()) sum += c;
        assertEquals(t.reachableCount(), sum);
        assertEquals(1, t.histogram()[0]);
        assertEquals(2, t.histogram()[1], "Đích có ô trống ở góc có đúng 2 trạng thái kề");
    }

    @Test
    void optimalMovesFollowGradient() {
        Goal goal = Goal.standard(3);
        ExactDistanceTable t = ExactDistanceTable.forGoal(goal);
        Board b = Board.parse("8 6 7 2 5 4 3 0 1"); // một trạng thái 31 bước
        assertEquals(31, t.distance(b));
        int steps = 0;
        while (!goal.isGoal(b)) {
            Move m = t.optimalMove(b);
            assertNotNull(m);
            b = b.move(m);
            steps++;
        }
        assertEquals(31, steps);
    }

    @Test
    void rejectsLargeBoards() {
        assertThrows(IllegalArgumentException.class, () -> ExactDistanceTable.forGoal(Goal.standard(4)));
    }
}
