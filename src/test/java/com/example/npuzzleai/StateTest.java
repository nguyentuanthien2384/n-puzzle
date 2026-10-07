package com.example.npuzzleai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StateTest {
    @AfterEach
    void resetStatics() {
        State.goal = 1;
        State.heuristic = 1;
        BFS.stop = false;
        AStar.stop = false;
    }

    @Test
    void supportsBothGoalLayouts() {
        State.goal = 1;
        State first = new State(3);
        assertArrayEquals(new int[]{0,1,2,3,4,5,6,7,8}, first.createGoalArray());

        State.goal = 2;
        State second = new State(3);
        assertArrayEquals(new int[]{1,2,3,4,5,6,7,8,0}, second.createGoalArray());
    }

    @Test
    void heuristicsWorkWithBlankAtEndGoal() {
        State.goal = 2;
        State goal = new State(3);
        goal.createGoalArray();
        State current = new State(new int[]{1,2,3,4,5,6,7,0,8}, 3);

        assertEquals(1, current.heuristic1(goal));
        assertEquals(1, current.heuristic2(goal));
        assertEquals(1, current.heuristic3(goal));
        assertEquals(1, current.heuristic4(goal));
        assertEquals(1, current.heuristic5(goal));
    }

    @Test
    void randomStateIsAlwaysSolvable() {
        for (int goalKind = 1; goalKind <= 2; goalKind++) {
            State.goal = goalKind;
            State goal = new State(4);
            goal.createGoalArray();
            for (int i = 0; i < 10; i++) {
                State random = new State(4);
                random.createRandomArray();
                assertTrue(random.isSolvable(goal));
            }
        }
    }

    @Test
    void detectsUnsolvableState() {
        State.goal = 2;
        State goal = new State(new int[]{1,2,3,4,5,6,7,8,0}, 3);
        State impossible = new State(new int[]{1,2,3,4,5,6,8,7,0}, 3);
        assertFalse(impossible.isSolvable(goal));
    }
}
