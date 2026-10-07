package com.example.npuzzleai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Vector;

/**
 * Greedy Best-First Search.
 *
 * <p>Trong "họ" tìm kiếm heuristic, A* đánh giá f = g + h (cân bằng chi phí đã đi
 * và ước lượng còn lại), còn Greedy Best-First chỉ dùng f = h: luôn mở rộng node
 * trông gần đích nhất. Rất nhanh, ít bộ nhớ, nhưng KHÔNG đảm bảo lời giải ngắn nhất -
 * hữu ích khi cần lời giải tạm nhanh cho bảng lớn.</p>
 */
public class GreedyBestFirst {
    public Node startNode;
    public Node goalNode;
    public Node currentNode;
    public final Vector<int[]> RESULT = new Vector<>();
    public int approvedNodes;
    public int totalNodes;
    public long time;
    public String error;
    public static volatile boolean stop = false;

    private static final long TIME_LIMIT_MS = 60_000L;

    private final PriorityQueue<Node> fringe = new PriorityQueue<>(
            Comparator.comparingInt((Node n) -> n.h).thenComparingInt(n -> n.g)
    );

    public void solve() {
        reset();
        if (!validateInput()) return;
        if (!startNode.state.isSolvable(goalNode.state)) {
            error = "Trạng thái hiện tại không thể biến đổi về trạng thái đích đã chọn.";
            return;
        }

        long startTime = System.currentTimeMillis();
        Map<State, Integer> bestG = new HashMap<>();
        Set<State> closed = new HashSet<>();

        startNode.parent = null;
        startNode.g = 0;
        startNode.h = startNode.estimate(goalNode.state);
        fringe.add(startNode);
        bestG.put(startNode.state, 0);

        while (!fringe.isEmpty()) {
            if (cancelled(startTime)) return;

            currentNode = fringe.poll();
            Integer bestKnown = bestG.get(currentNode.state);
            if (bestKnown == null || currentNode.g != bestKnown || closed.contains(currentNode.state)) {
                continue; // bản ghi cũ trong hàng đợi
            }
            closed.add(currentNode.state);
            approvedNodes++;

            if (currentNode.equals(goalNode)) {
                time = System.currentTimeMillis() - startTime;
                totalNodes = bestG.size();
                addResult(currentNode);
                fringe.clear();
                return;
            }

            for (Node child : currentNode.successors()) {
                int tentativeG = currentNode.g + child.cost;
                if (tentativeG >= bestG.getOrDefault(child.state, Integer.MAX_VALUE)) continue;

                child.parent = currentNode;
                child.g = tentativeG;
                child.h = child.estimate(goalNode.state);
                bestG.put(child.state, tentativeG);
                fringe.add(child);
                totalNodes++;
            }
        }

        time = System.currentTimeMillis() - startTime;
        totalNodes = bestG.size();
        error = "Không tồn tại lời giải cho trạng thái hiện tại.";
        fringe.clear();
    }

    private boolean cancelled(long startTime) {
        if (stop || Thread.currentThread().isInterrupted()) {
            error = "Đã dừng tìm kiếm theo yêu cầu.";
            time = System.currentTimeMillis() - startTime;
            fringe.clear();
            return true;
        }
        if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
            error = "Thuật toán quá tốn thời gian (vượt quá 60 giây).";
            time = System.currentTimeMillis() - startTime;
            fringe.clear();
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
        String heuristicError = State.heuristicSizeError(startNode.state.getSize());
        if (heuristicError != null) {
            error = heuristicError;
            return false;
        }
        return true;
    }

    private void reset() {
        RESULT.clear();
        fringe.clear();
        currentNode = null;
        approvedNodes = 0;
        totalNodes = 0;
        time = 0;
        error = null;
    }

    public void addResult(Node node) {
        List<int[]> reversed = new ArrayList<>();
        Node cursor = node;
        while (cursor != null) {
            reversed.add(cursor.state.value.clone());
            cursor = cursor.parent;
        }
        Collections.reverse(reversed);
        RESULT.addAll(reversed);
    }
}
