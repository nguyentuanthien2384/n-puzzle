package com.example.npuzzleai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

/**
 * IDA* - Iterative Deepening A* (Korf 1985).
 *
 * <p>Thay vì giữ hàng đợi ưu tiên như A*, IDA* lặp deepen theo ngưỡng f = g + h:
 * mỗi vòng chỉ đi sâu (DFS) tới các node có f <= ngưỡng; ngưỡng kế tiếp bằng
 * giá trị f nhỏ nhất bị cắt. Chỉ tốn bộ nhớ theo độ sâu lời giải nên phù hợp
 * bảng 4x4/5x5, nơi A* dễ tràn bộ nhớ. Thuật toán dùng heuristic đang chọn
 * (State.heuristic); lời giải tối ưu khi heuristic chấp nhận được.</p>
 */
public class IDAStar {
    public Node startNode;
    public Node goalNode;
    public Node currentNode;
    public final Vector<int[]> RESULT = new Vector<>();
    public int approvedNodes;   // số node đã mở rộng
    public int totalNodes;      // tổng số node sinh ra
    public long time;
    public String error;
    public static volatile boolean stop = false;

    private static final long TIME_LIMIT_MS = 60_000L;
    private static final int INFINITY = Integer.MAX_VALUE;
    private static final int FOUND = -1;

    private int threshold;
    private long startTime;
    private boolean aborted;

    public void solve() {
        reset();
        if (!validateInput()) return;
        if (!startNode.state.isSolvable(goalNode.state)) {
            error = "Trạng thái hiện tại không thể biến đổi về trạng thái đích đã chọn.";
            return;
        }

        startTime = System.currentTimeMillis();
        State goal = goalNode.state;
        Node root = new Node(State.unchecked(startNode.state.getSize(), startNode.state.value.clone()), 0);
        root.parent = null;
        root.g = 0;
        root.h = root.estimate(goal);
        root.f = root.g + root.h;
        threshold = root.h;

        while (true) {
            int t = search(root, goal);
            if (t == FOUND) {
                time = System.currentTimeMillis() - startTime;
                addResult(currentNode);
                return;
            }
            if (aborted) {
                time = System.currentTimeMillis() - startTime;
                error = stop || Thread.currentThread().isInterrupted()
                        ? "Đã dừng tìm kiếm theo yêu cầu."
                        : "Thuật toán quá tốn thời gian (vượt quá 60 giây).";
                return;
            }
            if (t == INFINITY) {
                time = System.currentTimeMillis() - startTime;
                error = "Không tồn tại lời giải cho trạng thái hiện tại.";
                return;
            }
            threshold = t;
        }
    }

    /** DFS có giới hạn f <= threshold; trả về FOUND, hoặc ngưỡng f nhỏ nhất bị cắt. */
    private int search(Node node, State goal) {
        if ((totalNodes & 0x3FF) == 0 && checkAborted()) return INFINITY;

        int f = node.g + node.h;
        if (f > threshold) return f;
        if (node.state.isGoal(goal)) {
            currentNode = node;
            return FOUND;
        }
        approvedNodes++;

        int minExcess = INFINITY;
        State avoid = node.parent != null ? node.parent.state : null;
        int size = node.state.getSize();
        int blank = node.state.posBlank(node.state.value);
        int row = blank / size, col = blank % size;

        for (int dir = 0; dir < 4; dir++) {
            int tr = row + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
            int tc = col + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
            if (tr < 0 || tr >= size || tc < 0 || tc >= size) continue;

            int target = tr * size + tc;
            int[] childValue = node.state.value.clone();
            childValue[blank] = childValue[target];
            childValue[target] = 0;
            State childState = State.unchecked(size, childValue);
            if (childState.equals(avoid)) continue; // bỏ nước đi lùi lại ngay bước trước
            totalNodes++;

            Node child = new Node(childState, 1);
            child.parent = node;
            child.g = node.g + 1;
            child.h = child.estimate(goal);
            child.f = child.g + child.h;

            int t = search(child, goal);
            if (t == FOUND) return FOUND;
            if (aborted) return INFINITY;
            if (t < minExcess) minExcess = t;
        }
        return minExcess;
    }

    private boolean checkAborted() {
        if (stop || Thread.currentThread().isInterrupted()) {
            aborted = true;
            return true;
        }
        if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
            aborted = true;
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
        currentNode = null;
        approvedNodes = 0;
        totalNodes = 0;
        time = 0;
        error = null;
        aborted = false;
        threshold = 0;
    }

    // Truy vết đường đi từ node đích về gốc theo chiều start -> goal.
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
