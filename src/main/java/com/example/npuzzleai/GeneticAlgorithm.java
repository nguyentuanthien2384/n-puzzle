package com.example.npuzzleai;

import java.util.Arrays;
import java.util.Random;
import java.util.Vector;

/**
 * Genetic Algorithm - thuật toán di truyền (tìm kiếm cục bộ, Russell &amp; Norvig).
 *
 * <p>Mỗi cá thể là một "nhiễm sắc thể" = chuỗi nước đi (0=Lên, 1=Xuống, 2=Trái, 3=Phải).
 * Độ thích nghi (fitness) đánh giá bằng khoảng cách Manhattan tới đích sau khi áp chuỗi
 * nước đi (nước đi không hợp lệ bị bỏ qua), cộng thêm ưu tiên chuỗi ngắn. Quần thể
 * tiến hóa qua tuyển chọn giải đấu, lai ghép một điểm và đột biến. GA không đảm bảo
 * tìm được lời giải; nếu tìm được thì lời giải hầu như luôn không tối ưu. Chỉ mở cho 3x3.</p>
 */
public class GeneticAlgorithm {
    public Node startNode;
    public Node goalNode;
    public final Vector<int[]> RESULT = new Vector<>();
    public int approvedNodes;   // số cá thể đã đánh giá
    public int totalNodes;      // tổng nước đi đã mô phỏng
    public long time;
    public String error;
    public static volatile boolean stop = false;

    private static final long TIME_LIMIT_MS = 60_000L;
    private static final int POPULATION_SIZE = 200;
    private static final int CHROMOSOME_LENGTH = 80;
    private static final int MAX_GENERATIONS = 1000;
    private static final int ELITE_COUNT = 12;
    private static final int TOURNAMENT_SIZE = 3;
    private static final double CROSSOVER_RATE = 0.9;
    private static final double MUTATION_RATE = 0.04;

    private final Random random = new Random(20261006L);

    public void solve() {
        reset();
        if (!validateInput()) return;

        long startTime = System.currentTimeMillis();
        int length = startNode.state.getLength();
        int size = startNode.state.getSize();
        int[] goalValue = goalNode.state.value.clone();
        int[] goalPos = new int[length];
        for (int i = 0; i < length; i++) {
            goalPos[goalValue[i]] = i;
        }

        int[][] population = new int[POPULATION_SIZE][CHROMOSOME_LENGTH];
        for (int[] chromosome : population) {
            for (int g = 0; g < CHROMOSOME_LENGTH; g++) {
                chromosome[g] = random.nextInt(4);
            }
        }
        double[] fitness = new double[POPULATION_SIZE];
        int[] board = new int[length];

        for (int generation = 0; generation < MAX_GENERATIONS; generation++) {
            if (cancelled(startTime)) return;

            int bestIndex = -1;
            double bestFitness = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < POPULATION_SIZE; i++) {
                approvedNodes++;
                totalNodes += CHROMOSOME_LENGTH;
                ApplyOutcome outcome = applyChromosome(population[i], board, size);
                if (outcome.solved) {
                    decodeResult(board, population[i], size);
                    Paths.compress(RESULT);
                    time = System.currentTimeMillis() - startTime;
                    return;
                }
                fitness[i] = -(1000.0 * manhattan(board, goalPos, size) + outcome.effectiveMoves);
                if (fitness[i] > bestFitness) {
                    bestFitness = fitness[i];
                    bestIndex = i;
                }
            }

            // Tạo thế hệ mới: ưu tú giữ nguyên + sinh con qua tuyển chọn, lai ghép, đột biến.
            int[][] next = new int[POPULATION_SIZE][CHROMOSOME_LENGTH];
            int[] order = indicesSortedByFitness(fitness);
            for (int e = 0; e < ELITE_COUNT; e++) {
                next[e] = population[order[e]].clone();
            }
            for (int i = ELITE_COUNT; i < POPULATION_SIZE; i++) {
                int[] parentA = tournament(population, fitness);
                int[] parentB = tournament(population, fitness);
                int[] child = new int[CHROMOSOME_LENGTH];
                if (random.nextDouble() < CROSSOVER_RATE) {
                    int cut = random.nextInt(CHROMOSOME_LENGTH);
                    System.arraycopy(parentA, 0, child, 0, cut);
                    System.arraycopy(parentB, cut, child, cut, CHROMOSOME_LENGTH - cut);
                } else {
                    System.arraycopy(parentA, 0, child, 0, CHROMOSOME_LENGTH);
                }
                for (int g = 0; g < CHROMOSOME_LENGTH; g++) {
                    if (random.nextDouble() < MUTATION_RATE) {
                        child[g] = random.nextInt(4);
                    }
                }
                next[i] = child;
            }
            population = next;
        }

        time = System.currentTimeMillis() - startTime;
        error = "Genetic Algorithm không tìm được lời giải sau " + MAX_GENERATIONS
                + " thế hệ - đây là giới hạn vốn có của thuật toán di truyền.";
    }

    /** Áp chuỗi nước đi lên bản sao bàn cờ bắt đầu; trả về trạng thái cuối và số bước hiệu lực. */
    private ApplyOutcome applyChromosome(int[] chromosome, int[] board, int size) {
        System.arraycopy(startNode.state.value, 0, board, 0, board.length);
        int blank = 0;
        while (board[blank] != 0) blank++;
        int effective = 0;
        for (int g = 0; g < chromosome.length; g++) {
            int dir = chromosome[g];
            int tr = blank / size + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
            int tc = blank % size + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
            if (tr < 0 || tr >= size || tc < 0 || tc >= size) continue;
            int target = tr * size + tc;
            board[blank] = board[target];
            board[target] = 0;
            blank = target;
            effective++;
        }
        boolean solved = Arrays.equals(board, goalNode.state.value);
        return new ApplyOutcome(solved, effective);
    }

    /** Giải mã lời giải: phát lại chuỗi nước đi của cá thể thắng và ghi từng trạng thái. */
    private void decodeResult(int[] board, int[] chromosome, int size) {
        System.arraycopy(startNode.state.value, 0, board, 0, board.length);
        RESULT.add(board.clone());
        int blank = 0;
        while (board[blank] != 0) blank++;
        for (int g = 0; g < chromosome.length && !Arrays.equals(board, goalNode.state.value); g++) {
            int dir = chromosome[g];
            int tr = blank / size + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
            int tc = blank % size + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
            if (tr < 0 || tr >= size || tc < 0 || tc >= size) continue;
            int target = tr * size + tc;
            board[blank] = board[target];
            board[target] = 0;
            blank = target;
            RESULT.add(board.clone());
        }
    }

    private int manhattan(int[] board, int[] goalPos, int size) {
        int distance = 0;
        for (int i = 0; i < board.length; i++) {
            int tile = board[i];
            if (tile == 0) continue;
            int gi = goalPos[tile];
            distance += Math.abs(gi / size - i / size) + Math.abs(gi % size - i % size);
        }
        return distance;
    }

    private int[] tournament(int[][] population, double[] fitness) {
        int best = random.nextInt(POPULATION_SIZE);
        for (int t = 1; t < TOURNAMENT_SIZE; t++) {
            int contender = random.nextInt(POPULATION_SIZE);
            if (fitness[contender] > fitness[best]) best = contender;
        }
        return population[best];
    }

    private int[] indicesSortedByFitness(double[] fitness) {
        Integer[] order = new Integer[POPULATION_SIZE];
        for (int i = 0; i < POPULATION_SIZE; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(fitness[b], fitness[a]));
        int[] result = new int[POPULATION_SIZE];
        for (int i = 0; i < POPULATION_SIZE; i++) result[i] = order[i];
        return result;
    }

    private boolean cancelled(long startTime) {
        if (stop || Thread.currentThread().isInterrupted()) {
            error = "Đã dừng tìm kiếm theo yêu cầu.";
            time = System.currentTimeMillis() - startTime;
            return true;
        }
        if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
            error = "Thuật toán quá tốn thời gian (vượt quá 60 giây).";
            time = System.currentTimeMillis() - startTime;
            return true;
        }
        return false;
    }

    private boolean validateInput() {
        if (startNode == null || goalNode == null) {
            error = "Chưa thiết lập trạng thái bắt đầu hoặc trạng thái đích.";
            return false;
        }
        if (startNode.state.getSize() != goalNode.state.getSize()) {
            error = "Kích thước trạng thái bắt đầu và trạng thái đích không khớp.";
            return false;
        }
        if (startNode.state.getSize() != 3) {
            error = "Genetic Algorithm chỉ mở cho bảng 3x3: tìm kiếm cục bộ không đảm bảo tìm được lời giải trên không gian lớn.";
            return false;
        }
        if (!startNode.state.isSolvable(goalNode.state)) {
            error = "Trạng thái hiện tại không thể biến đổi về trạng thái đích đã chọn.";
            return false;
        }
        return true;
    }

    private void reset() {
        RESULT.clear();
        approvedNodes = 0;
        totalNodes = 0;
        time = 0;
        error = null;
    }

    private record ApplyOutcome(boolean solved, int effectiveMoves) {
    }
}
