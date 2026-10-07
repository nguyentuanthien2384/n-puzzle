package com.example.npuzzleai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử cho heuristic H7/H8/H9 và các thuật toán mới:
 * IDA*, Greedy Best-First, Bidirectional BFS, Value Iteration,
 * Hill Climbing, Simulated Annealing, Genetic Algorithm.
 */
class AdvancedSearchTest {
    private static final int[] GOAL1_3 = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] GOAL2_3 = {1, 2, 3, 4, 5, 6, 7, 8, 0};
    private static final int[] GOAL1_4 = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};

    @AfterEach
    void resetState() {
        BFS.stop = false;
        AStar.stop = false;
        IDAStar.stop = false;
        GreedyBestFirst.stop = false;
        BidirectionalBFS.stop = false;
        ValueIteration.stop = false;
        HillClimbing.stop = false;
        SimulatedAnnealing.stop = false;
        GeneticAlgorithm.stop = false;
        State.heuristic = 1;
        State.goal = 1;
    }

    /** Xáo trạng thái bằng nước đi hợp lệ tính từ đích - luôn khả giải. */
    private static int[] scramble(int size, int[] goal, int moves, long seed) {
        State s = new State(size);
        s.value = goal.clone();
        Random r = new Random(seed);
        int prev = -1;
        for (int i = 0; i < moves; i++) {
            int blank = s.posBlank(s.value);
            java.util.List<Integer> candidates = new java.util.ArrayList<>();
            if (blank / size > 0) candidates.add(blank - size);
            if (blank / size < size - 1) candidates.add(blank + size);
            if (blank % size > 0) candidates.add(blank - 1);
            if (blank % size < size - 1) candidates.add(blank + 1);
            if (candidates.size() > 1) candidates.remove(Integer.valueOf(prev));
            int next = candidates.get(r.nextInt(candidates.size()));
            s.value[blank] = s.value[next];
            s.value[next] = 0;
            prev = blank;
        }
        return s.value;
    }

    private static void assertValidPath(Vector<int[]> path, int[] start, int[] goal) {
        assertFalse(path.isEmpty(), "Đường đi rỗng");
        assertArrayEquals(start, path.get(0), "Phải bắt đầu từ trạng thái xuất phát");
        assertArrayEquals(goal, path.lastElement(), "Phải kết thúc ở đích");
        for (int i = 1; i < path.size(); i++) {
            assertTrue(Paths.validStep(path.get(i - 1), path.get(i)),
                    "Bước " + i + " không phải một nước đi hợp lệ");
        }
    }

    /** Bảng khoảng cách đúng cho 8-puzzle bằng BFS độc lập với code chính (rank Lehmer). */
    private static byte[] fullDistanceTable(int n, int[] goalValue) {
        int space = 1;
        for (int i = 2; i <= n; i++) space *= i;
        byte[] dist = new byte[space];
        Arrays.fill(dist, (byte) -1);
        int goalRank = Permutation.rank(goalValue, n);
        dist[goalRank] = 0;
        int[] queue = new int[space];
        int head = 0, tail = 0;
        queue[tail++] = goalRank;
        int[] current = new int[n];
        int[] child = new int[n];
        int size = (int) Math.round(Math.sqrt(n));
        while (head < tail) {
            int r = queue[head++];
            int d = dist[r];
            Permutation.unrank(r, n, current);
            int blank = 0;
            while (current[blank] != 0) blank++;
            int row = blank / size, col = blank % size;
            for (int dir = 0; dir < 4; dir++) {
                int tr = row + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
                int tc = col + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                if (tr < 0 || tr >= size || tc < 0 || tc >= size) continue;
                int target = tr * size + tc;
                System.arraycopy(current, 0, child, 0, n);
                child[blank] = child[target];
                child[target] = 0;
                int childRank = Permutation.rank(child, n);
                if (dist[childRank] == -1) {
                    dist[childRank] = (byte) (d + 1);
                    queue[tail++] = childRank;
                }
            }
        }
        return dist;
    }

    @Test
    void heuristics7And8AdmissibleOn8Puzzle() {
        for (int goalType = 1; goalType <= 2; goalType++) {
            int[] goal = goalType == 1 ? GOAL1_3 : GOAL2_3;
            State.goal = goalType;
            byte[] exact = fullDistanceTable(9, goal);
            State goalState = State.unchecked(3, goal.clone());

            Random r = new Random(42L);
            State board = State.unchecked(3, goal.clone());
            for (int step = 0; step < 300; step++) {
                var succ = board.successors();
                board = State.unchecked(3, succ.get(r.nextInt(succ.size())).value);
                if (step % 3 != 0) continue; // kiểm tra 100 trạng thái

                int trueDistance = exact[Permutation.rank(board.value, 9)];
                assertTrue(trueDistance >= 0, "Trạng thái phải khả giải");

                State.heuristic = 2;
                int h2 = board.estimate(goalState);
                State.heuristic = 7;
                int h7 = board.estimate(goalState);
                State.heuristic = 8;
                int h8 = board.estimate(goalState);
                State.heuristic = 9;
                int h9 = board.estimate(goalState);

                assertTrue(h7 <= trueDistance, "H7 phải chấp nhận được tại bước " + step);
                assertTrue(h8 <= trueDistance, "H8 phải chấp nhận được tại bước " + step);
                assertTrue(h9 == trueDistance, "H9 (bảng đầy đủ 3x3) phải chính xác tại bước " + step);
                assertTrue(h8 >= h2, "H8 phải trội không kém Manhattan");
                assertTrue(h7 >= 0 && h8 >= 0);
            }
        }
    }

    @Test
    void walkingDistanceConsistentOn8Puzzle() {
        State.goal = 2;
        int[] goal = GOAL2_3;
        State goalState = State.unchecked(3, goal.clone());
        State board = State.unchecked(3, scramble(3, goal, 25, 7L));
        State.heuristic = 7;
        int h = board.estimate(goalState);
        for (State succ : board.successors()) {
            int hNext = succ.estimate(goalState);
            assertTrue(Math.abs(h - hNext) <= 1, "Walking Distance phải nhất quán (|Δh| <= 1)");
        }
    }

    @Test
    void heuristicSizeRestriction() {
        int[] goal5 = new int[25];
        for (int i = 0; i < 25; i++) goal5[i] = i;
        State.heuristic = 9;
        assertNotNull(State.heuristicSizeError(5), "H9 phải bị chặn trên 5x5");
        State.heuristic = 7;
        assertNotNull(State.heuristicSizeError(5), "H7 phải bị chặn trên 5x5");
        State.heuristic = 2;
        assertNull(State.heuristicSizeError(5), "H2 vẫn dùng được trên 5x5");

        AStar solver = new AStar();
        solver.startNode = new Node(new State(scramble(5, goal5, 40, 3L), 5), 0);
        solver.goalNode = new Node(new State(goal5, 5), 0);
        State.heuristic = 9;
        solver.solve();
        assertNotNull(solver.error, "A* + PDB trên 5x5 phải báo lỗi thay vì tìm kiếm");
    }

    @Test
    void idaStarOptimalOn8PuzzleForAllAdmissibleHeuristics() {
        int[] goal = GOAL1_3;
        int[] start = scramble(3, goal, 20, 11L);
        int trueDistance = fullDistanceTable(9, goal)[Permutation.rank(start, 9)];

        for (int h : new int[]{1, 2, 3, 4, 7, 8, 9}) {
            State.heuristic = h;
            IDAStar solver = new IDAStar();
            solver.startNode = new Node(new State(start, 3), 0);
            solver.goalNode = new Node(new State(goal, 3), 0);
            solver.solve();
            assertNull(solver.error, "IDA* H" + h + " phải tìm được lời giải");
            assertEquals(trueDistance, solver.RESULT.size() - 1,
                    "IDA* H" + h + " phải tối ưu");
            assertValidPath(solver.RESULT, start, goal);
        }
    }

    @Test
    void idaStarMatchesAStarOn4x4() {
        int[] goal = GOAL1_4;
        int[] start = scramble(4, goal, 24, 21L);

        State.heuristic = 2;
        AStar astar = new AStar();
        astar.startNode = new Node(new State(start, 4), 0);
        astar.goalNode = new Node(new State(goal, 4), 0);
        astar.solve();
        assertNull(astar.error);
        int optimal = astar.RESULT.size() - 1;

        State.heuristic = 8;
        IDAStar solver = new IDAStar();
        solver.startNode = new Node(new State(start, 4), 0);
        solver.goalNode = new Node(new State(goal, 4), 0);
        solver.solve();
        assertNull(solver.error, "IDA* + H8 phải giải được 4x4");
        assertEquals(optimal, solver.RESULT.size() - 1, "IDA* + H8 phải tối ưu như A* + H2");
        assertValidPath(solver.RESULT, start, goal);
    }

    @Test
    void patternDatabase4x4AdmissibleAndOptimal() {
        int[] goal = GOAL1_4;
        int[] start = scramble(4, goal, 30, 33L);

        State.heuristic = 2;
        AStar astarH2 = new AStar();
        astarH2.startNode = new Node(new State(start, 4), 0);
        astarH2.goalNode = new Node(new State(goal, 4), 0);
        astarH2.solve();
        assertNull(astarH2.error);
        int optimal = astarH2.RESULT.size() - 1;

        State.heuristic = 9;
        AStar astarPDB = new AStar();
        astarPDB.startNode = new Node(new State(start, 4), 0);
        astarPDB.goalNode = new Node(new State(goal, 4), 0);
        astarPDB.solve();
        assertNull(astarPDB.error, "A* + PDB phải tìm được lời giải");
        assertEquals(optimal, astarPDB.RESULT.size() - 1, "A* + PDB phải cho độ dài tối ưu");
        assertValidPath(astarPDB.RESULT, start, goal);
    }

    @Test
    void greedyFindsValidSolution() {
        int[] goal = GOAL1_3;
        int[] start = scramble(3, goal, 10, 5L);
        State.heuristic = 2;
        GreedyBestFirst solver = new GreedyBestFirst();
        solver.startNode = new Node(new State(start, 3), 0);
        solver.goalNode = new Node(new State(goal, 3), 0);
        solver.solve();
        assertNull(solver.error);
        assertValidPath(solver.RESULT, start, goal);
    }

    @Test
    void bidirectionalBfsOptimalOn8Puzzle() {
        State.goal = 2;
        int[] goal = GOAL2_3;
        int[] start = scramble(3, goal, 18, 9L);
        int trueDistance = fullDistanceTable(9, goal)[Permutation.rank(start, 9)];

        BidirectionalBFS solver = new BidirectionalBFS();
        solver.startNode = new Node(new State(start, 3), 0);
        solver.goalNode = new Node(new State(goal, 3), 0);
        solver.solve();
        assertNull(solver.error);
        assertEquals(trueDistance, solver.RESULT.size() - 1, "Bidirectional BFS phải tối ưu");
        assertValidPath(solver.RESULT, start, goal);
    }

    @Test
    void valueIterationFindsOptimalPolicy() {
        for (int goalType = 1; goalType <= 2; goalType++) {
            State.goal = goalType;
            int[] goal = goalType == 1 ? GOAL1_3 : GOAL2_3;
            int[] start = scramble(3, goal, 12, 13L + goalType);
            int trueDistance = fullDistanceTable(9, goal)[Permutation.rank(start, 9)];

            ValueIteration solver = new ValueIteration();
            solver.startNode = new Node(new State(start, 3), 0);
            solver.goalNode = new Node(new State(goal, 3), 0);
            solver.solve();
            assertNull(solver.error, "Value Iteration phải hội tụ (đích " + goalType + ")");
            assertEquals(trueDistance, solver.RESULT.size() - 1,
                    "Chính sách trích xuất phải tối ưu (đích " + goalType + ")");
            assertValidPath(solver.RESULT, start, goal);
        }
    }

    @Test
    void valueIterationRejectsUnsolvableState() {
        // Đổi chỗ hai ô số làm lệch tính khả giải
        int[] start = {1, 2, 3, 4, 5, 6, 8, 7, 0};
        ValueIteration solver = new ValueIteration();
        solver.startNode = new Node(new State(start, 3), 0);
        solver.goalNode = new Node(new State(GOAL1_3, 3), 0);
        solver.solve();
        assertNotNull(solver.error, "Trạng thái vô nghiệm phải bị phát hiện");
        assertTrue(solver.RESULT.isEmpty());
    }

    @Test
    void hillClimbingSolvesNearGoalInstance() {
        int[] goal = GOAL1_3;
        int[] start = scramble(3, goal, 6, 15L);
        HillClimbing solver = new HillClimbing();
        solver.startNode = new Node(new State(start, 3), 0);
        solver.goalNode = new Node(new State(goal, 3), 0);
        solver.solve();
        assertNull(solver.error, "Hill Climbing phải giải được trạng thái gần đích: " + solver.error);
        assertValidPath(solver.RESULT, start, goal);
    }

    @Test
    void simulatedAnnealingSolvesNearGoalInstance() {
        State.goal = 2;
        int[] goal = GOAL2_3;
        int[] start = scramble(3, goal, 8, 17L);
        SimulatedAnnealing solver = new SimulatedAnnealing();
        solver.startNode = new Node(new State(start, 3), 0);
        solver.goalNode = new Node(new State(goal, 3), 0);
        solver.solve();
        assertNull(solver.error, "Simulated Annealing phải giải được trạng thái gần đích: " + solver.error);
        assertValidPath(solver.RESULT, start, goal);
    }

    @Test
    void geneticAlgorithmSolvesNearGoalInstance() {
        int[] goal = GOAL1_3;
        int[] start = scramble(3, goal, 4, 19L);
        GeneticAlgorithm solver = new GeneticAlgorithm();
        solver.startNode = new Node(new State(start, 3), 0);
        solver.goalNode = new Node(new State(goal, 3), 0);
        solver.solve();
        assertNull(solver.error, "GA phải giải được trạng thái rất gần đích: " + solver.error);
        assertValidPath(solver.RESULT, start, goal);
    }

    @Test
    void pathCompressionRemovesBacktracking() {
        // Goal: {0..8}. B cách goal 1 nước (ô trống từ 0 xuống 3), S cách B 1 nước
        // (ô trống từ 3 sang 4), A là hàng xóm khác của S. Đường đi S -> A -> S -> B -> goal
        // chứa cặp nước đi trái chiều và phải rút gọn thành S -> B -> goal.
        int[] start = {3, 1, 2, 4, 0, 5, 6, 7, 8};
        int[] a = {3, 0, 2, 4, 1, 5, 6, 7, 8};
        int[] b = {3, 1, 2, 0, 4, 5, 6, 7, 8};
        int[] goal = {0, 1, 2, 3, 4, 5, 6, 7, 8};
        Vector<int[]> path = new Vector<>();
        path.add(start.clone());
        path.add(a.clone());
        path.add(start.clone());
        path.add(b.clone());
        path.add(goal.clone());
        Paths.compress(path);
        assertEquals(3, path.size(), "Đường đi phải được rút gọn còn 3 trạng thái");
        assertValidPath(path, start, goal);
    }

    @Test
    void localSearchLimitedTo3x3() {
        int[] goal = GOAL1_4;
        int[] start = scramble(4, goal, 10, 23L);

        HillClimbing hc = new HillClimbing();
        hc.startNode = new Node(new State(start, 4), 0);
        hc.goalNode = new Node(new State(goal, 4), 0);
        hc.solve();
        assertNotNull(hc.error, "Hill Climbing phải từ chối 4x4");

        SimulatedAnnealing sa = new SimulatedAnnealing();
        sa.startNode = new Node(new State(start, 4), 0);
        sa.goalNode = new Node(new State(goal, 4), 0);
        sa.solve();
        assertNotNull(sa.error, "Simulated Annealing phải từ chối 4x4");

        GeneticAlgorithm ga = new GeneticAlgorithm();
        ga.startNode = new Node(new State(start, 4), 0);
        ga.goalNode = new Node(new State(goal, 4), 0);
        ga.solve();
        assertNotNull(ga.error, "Genetic Algorithm phải từ chối 4x4");

        ValueIteration vi = new ValueIteration();
        vi.startNode = new Node(new State(start, 4), 0);
        vi.goalNode = new Node(new State(goal, 4), 0);
        vi.solve();
        assertNotNull(vi.error, "Value Iteration phải từ chối 4x4");
    }
}
