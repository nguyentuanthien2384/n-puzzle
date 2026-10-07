package com.example.npuzzleai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;

/**
 * Hill Climbing - leo đồi (tìm kiếm cục bộ, Russell &amp; Norvig).
 *
 * <p>Từ trạng thái hiện tại luôn chọn trạng thái kế tiếp có heuristic nhỏ nhất
 * (leo dốc nhất). Bản thuần chủng dễ kẹt cực trị địa phương/bình nguyên, nên ở đây
 * dùng hai kỹ thuật bổ trợ: đi ngang (sideways move) trong bình nguyên và bước thoát
 * ngẫu nhiên khi bị kẹt. Đây là thuật toán "không có nhớ, không quay lui": đường đi
 * ghi nhận được là chuỗi nước đi thực tế, thường không tối ưu. Vì không gian lớn
 * khiến leo đồi gần như không bao giờ tới đích, chỉ mở cho bảng 3x3.</p>
 */
public class HillClimbing {
    public Node startNode;
    public Node goalNode;
    public final Vector<int[]> RESULT = new Vector<>();
    public int approvedNodes;   // số bước đã đi
    public int totalNodes;      // số trạng thái kế tiếp đã đánh giá
    public long time;
    public String error;
    public static volatile boolean stop = false;

    private static final long TIME_LIMIT_MS = 60_000L;
    private static final int MAX_STEPS = 20_000;
    private static final int SIDEWAYS_LIMIT = 40;

    private final Random random = new Random(20261006L);

    public void solve() {
        reset();
        if (!validateInput()) return;

        long startTime = System.currentTimeMillis();
        State goal = goalNode.state;
        State current = State.unchecked(startNode.state.getSize(), startNode.state.value.clone());
        RESULT.add(current.value.clone());

        int sideways = 0;
        while (!current.isGoal(goal)) {
            if (cancelled(startTime)) return;
            if (RESULT.size() > MAX_STEPS) {
                error = "Hill Climbing kẹt trong cực trị địa phương sau " + MAX_STEPS + " bước (không leo tới đích).";
                return;
            }

            Vector<State> successors = current.successors();
            int currentH = current.estimate(goal);
            int bestH = Integer.MAX_VALUE;
            List<State> best = new ArrayList<>();
            for (State successor : successors) {
                int h = successor.estimate(goal);
                totalNodes++;
                if (h < bestH) {
                    bestH = h;
                    best.clear();
                    best.add(successor);
                } else if (h == bestH) {
                    best.add(successor);
                }
            }

            State next;
            if (bestH < currentH) {
                next = best.get(0);
                sideways = 0;
            } else if (bestH == currentH && sideways < SIDEWAYS_LIMIT) {
                next = best.get(random.nextInt(best.size())); // đi ngang qua bình nguyên
                sideways++;
            } else {
                next = successors.get(random.nextInt(successors.size())); // bước thoát ngẫu nhiên
                sideways = 0;
            }

            current = next;
            RESULT.add(current.value.clone());
            approvedNodes++;
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
            error = "Hill Climbing chỉ mở cho bảng 3x3: tìm kiếm cục bộ không đảm bảo tìm được lời giải trên không gian lớn.";
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
