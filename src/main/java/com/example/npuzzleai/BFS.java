package com.example.npuzzleai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;

/** Tìm kiếm theo chiều rộng (BFS), dùng tập visited để tránh lặp trạng thái. */
public class BFS {
    public Node startNode;
    public Node goalNode;
    public Node currentNode;
    private final Queue<Node> FRINGE = new ArrayDeque<>();
    public final Vector<int[]> RESULT = new Vector<>();
    protected int approvedNodes;
    protected int totalNodes;
    protected long time;
    protected static volatile boolean stop = false;
    protected String error;

    private static final long TIME_LIMIT_MS = 60_000L;
    private static final int MAX_VISITED_NODES = 1_500_000;

    public void solve() {
        reset();
        if (!validateInput()) return;

        // Theo chính báo cáo, BFS chỉ phù hợp thực tế với 3x3.
        if (startNode.state.getSize() > 3) {
            error = "BFS chỉ phù hợp cho bảng 3x3. Hãy dùng A* cho bảng 4x4 hoặc 5x5.";
            return;
        }
        if (!startNode.state.isSolvable(goalNode.state)) {
            error = "Trạng thái hiện tại không thể biến đổi về trạng thái đích đã chọn.";
            return;
        }

        long startTime = System.currentTimeMillis();
        Set<State> visited = new HashSet<>();
        startNode.parent = null;
        FRINGE.add(startNode);
        visited.add(startNode.state);

        try {
            while (!FRINGE.isEmpty()) {
                if (cancelled(startTime)) return;

                currentNode = FRINGE.poll();
                if (currentNode.equals(goalNode)) {
                    time = System.currentTimeMillis() - startTime;
                    totalNodes = visited.size();
                    addResult(currentNode);
                    return;
                }

                approvedNodes++;
                for (Node child : currentNode.successors()) {
                    if (visited.add(child.state)) {
                        child.parent = currentNode;
                        FRINGE.add(child);
                        if (visited.size() >= MAX_VISITED_NODES) {
                            error = "BFS đã đạt giới hạn bộ nhớ an toàn (" + MAX_VISITED_NODES + " trạng thái).";
                            totalNodes = visited.size();
                            time = System.currentTimeMillis() - startTime;
                            return;
                        }
                    }
                }
            }
            error = "Không tồn tại lời giải cho trạng thái hiện tại.";
            time = System.currentTimeMillis() - startTime;
            totalNodes = visited.size();
        } catch (OutOfMemoryError e) {
            error = "Tràn bộ nhớ khi chạy BFS. Hãy dùng A* hoặc trạng thái gần đích hơn.";
            totalNodes = visited.size();
            time = System.currentTimeMillis() - startTime;
        } finally {
            FRINGE.clear();
        }
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
        return true;
    }

    private boolean cancelled(long startTime) {
        if (stop || Thread.currentThread().isInterrupted()) {
            error = "Đã dừng tìm kiếm theo yêu cầu.";
            time = System.currentTimeMillis() - startTime;
            FRINGE.clear();
            return true;
        }
        if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
            error = "Thuật toán quá tốn thời gian (vượt quá 60 giây).";
            time = System.currentTimeMillis() - startTime;
            FRINGE.clear();
            return true;
        }
        return false;
    }

    private void reset() {
        RESULT.clear();
        FRINGE.clear();
        currentNode = null;
        approvedNodes = 0;
        totalNodes = 0;
        time = 0;
        error = null;
    }

    // Truy vết kết quả theo chiều start -> goal.
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
