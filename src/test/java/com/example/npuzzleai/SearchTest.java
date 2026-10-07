package com.example.npuzzleai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchTest {
    @AfterEach
    void resetStopFlags() {
        BFS.stop = false;
        AStar.stop = false;
        State.heuristic = 1;
        State.goal = 1;
    }

    @Test
    void bfsFindsShortestOneMovePath() {
        BFS solver = new BFS();
        solver.startNode = new Node(new State(new int[]{1,2,3,4,5,6,7,0,8}, 3), 0);
        solver.goalNode = new Node(new State(new int[]{1,2,3,4,5,6,7,8,0}, 3), 0);
        solver.solve();

        assertNull(solver.error);
        assertEquals(2, solver.RESULT.size());
        assertArrayEquals(new int[]{1,2,3,4,5,6,7,8,0}, solver.RESULT.lastElement());
    }

    @Test
    void aStarFindsOptimalPathForAllHeuristics() {
        int[] start = {1,2,3,4,5,6,0,7,8};
        int[] goal = {1,2,3,4,5,6,7,8,0};

        for (int h = 1; h <= 6; h++) {
            State.heuristic = h;
            AStar solver = new AStar();
            solver.startNode = new Node(new State(start, 3), 0);
            solver.goalNode = new Node(new State(goal, 3), 0);
            solver.solve();

            assertNull(solver.error, "H" + h + " phải tìm được lời giải");
            assertEquals(3, solver.RESULT.size(), "H" + h + " phải đi đúng 2 bước");
            assertArrayEquals(goal, solver.RESULT.lastElement());
        }
    }

    @Test
    void solversRejectImpossibleStateBeforeSearching() {
        int[] start = {1,2,3,4,5,6,8,7,0};
        int[] goal = {1,2,3,4,5,6,7,8,0};

        BFS bfs = new BFS();
        bfs.startNode = new Node(new State(start, 3), 0);
        bfs.goalNode = new Node(new State(goal, 3), 0);
        bfs.solve();
        assertNotNull(bfs.error);
        assertTrue(bfs.RESULT.isEmpty());

        AStar astar = new AStar();
        astar.startNode = new Node(new State(start, 3), 0);
        astar.goalNode = new Node(new State(goal, 3), 0);
        astar.solve();
        assertNotNull(astar.error);
        assertTrue(astar.RESULT.isEmpty());
    }
}
