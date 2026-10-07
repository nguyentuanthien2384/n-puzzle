package com.example.npuzzleai;

import java.util.Random;
import java.util.Vector;

/**
 * Simulated Annealing - mô phỏng ủ (tìm kiếm cục bộ, Russell &amp; Norvig).
 *
 * <p>Gợi ý từ quá trình ủ kim loại: ở "nhiệt độ" T cao, thuật toán dễ chấp nhận
 * nước đi làm xấu hơn (xác suất e^(-Δ/T)) để thoát cực trị địa phương; nhiệt độ
 * giảm dần theo lịch T = T * alpha khiến thuật toán dần "lắng" vào lời giải.
 * Không đảm bảo tìm được lời giải và lời giải thường không tối ưu. Chỉ mở cho 3x3.</p>
 */
public class SimulatedAnnealing {
    public Node startNode;
    public Node goalNode;
    public final Vector<int[]> RESULT = new Vector<>();
    public int approvedNodes;   // số bước đã đi
    public int totalNodes;      // số trạng thái kế tiếp đã đánh giá
    public long time;
    public String error;
    public static volatile boolean stop = false;

    private static final long TIME_LIMIT_MS = 60_000L;
    private static final int MAX_STEPS = 80_000;
    private static final double INITIAL_TEMPERATURE = 24.0;
    private static final double MIN_TEMPERATURE = 0.02;
    private static final double COOLING_RATE = 0.9995;
    private static final int MAX_REHEATS = 8;

    private final Random random = new Random(20261006L);

    public void solve() {
        reset();
        if (!validateInput()) return;

        long startTime = System.currentTimeMillis();
        State goal = goalNode.state;
        State current = State.unchecked(startNode.state.getSize(), startNode.state.value.clone());
        RESULT.add(current.value.clone());
        double temperature = INITIAL_TEMPERATURE;
        int reheats = 0;

        while (!current.isGoal(goal)) {
            if (cancelled(startTime)) return;
            if (temperature < MIN_TEMPERATURE) {
                if (reheats >= MAX_REHEATS) {
                    error = "Simulated Annealing không tới đích sau " + (reheats + 1)
                            + " chu kỳ làm nguội (" + approvedNodes + " bước).";
                    return;
                }
                // Hâm lại (reheating): nhiệt độ tăng trở lại để thoát cực trị địa phương.
                reheats++;
                temperature = INITIAL_TEMPERATURE / reheats;
            }
            if (RESULT.size() > MAX_STEPS) {
                error = "Simulated Annealing vượt quá " + MAX_STEPS + " bước mà chưa tới đích.";
                return;
            }

            Vector<State> successors = current.successors();
            State next = successors.get(random.nextInt(successors.size()));
            totalNodes++;

            int delta = next.estimate(goal) - current.estimate(goal);
            if (delta <= 0 || random.nextDouble() < Math.exp(-delta / temperature)) {
                current = next;
                RESULT.add(current.value.clone());
                approvedNodes++;
            }
            temperature *= COOLING_RATE;
        }

        Paths.compress(RESULT);
        time = System.currentTimeMillis() - startTime;
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
            error = "Simulated Annealing chỉ mở cho bảng 3x3: tìm kiếm cục bộ không đảm bảo tìm được lời giải trên không gian lớn.";
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
}
