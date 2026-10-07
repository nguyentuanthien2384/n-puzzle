package com.example.npuzzleai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * Tìm kiếm hai chiều (Bidirectional BFS).
 *
 * <p>Chạy đồng thời hai lượt BFS: một lượt từ trạng thái bắt đầu tiến về đích,
 * một lượt từ đích lùi về bắt đầu. Mỗi lần mở rộng nguyên một mức, luôn chọn
 * phía có rìa (frontier) nhỏ hơn; khi một trạng thái mới xuất hiện ở cả hai phía
 * thì hai đường gặp nhau. Độ sâu mỗi phía chỉ khoảng một nửa lời giải nên số
 * trạng thái phải nhớ giảm theo cấp số nhân so với BFS một chiều.</p>
 */
public class BidirectionalBFS {
    public Node startNode;
    public Node goalNode;
    public final Vector<int[]> RESULT = new Vector<>();
    public int approvedNodes;   // số node đã mở rộng
    public int totalNodes;      // tổng số trạng thái đã thăm
    public long time;
    public String error;
    public static volatile boolean stop = false;

    private static final long TIME_LIMIT_MS = 60_000L;
    private static final int MAX_VISITED_NODES = 1_500_000;

    public void solve() {
        reset();
        if (!validateInput()) return;
        if (!startNode.state.isSolvable(goalNode.state)) {
            error = "Trạng thái hiện tại không thể biến đổi về trạng thái đích đã chọn.";
            return;
        }

        long startTime = System.currentTimeMillis();

        Node startClone = new Node(new State(startNode.state.value, startNode.state.getSize()), 0);
        Node goalClone = new Node(new State(goalNode.state.value, goalNode.state.getSize()), 0);
        startClone.parent = null;
        startClone.g = 0;
        goalClone.parent = null;
        goalClone.g = 0;

        if (startClone.equals(goalClone)) {
            RESULT.add(startClone.state.value.clone());
            time = System.currentTimeMillis() - startTime;
            return;
        }

        Map<State, Node> visitedForward = new HashMap<>();
        Map<State, Node> visitedBackward = new HashMap<>();
        List<Node> frontierForward = new ArrayList<>();
        List<Node> frontierBackward = new ArrayList<>();
        visitedForward.put(startClone.state, startClone);
        visitedBackward.put(goalClone.state, goalClone);
        frontierForward.add(startClone);
        frontierBackward.add(goalClone);

        Node meetFromStart = null;
        Node meetFromGoal = null;
        int bestMeetTotal = Integer.MAX_VALUE;

        while (!frontierForward.isEmpty() && !frontierBackward.isEmpty()) {
            if (cancelled(startTime)) return;

            boolean expandForward = frontierForward.size() <= frontierBackward.size();
            List<Node> frontier = expandForward ? frontierForward : frontierBackward;
            Map<State, Node> ownVisited = expandForward ? visitedForward : visitedBackward;
            Map<State, Node> otherVisited = expandForward ? visitedBackward : visitedForward;

            List<Node> nextLevel = new ArrayList<>();
            for (Node node : frontier) {
                approvedNodes++;
                for (Node child : node.successors()) {
                    if (ownVisited.containsKey(child.state)) continue;
                    child.parent = node;
                    child.g = node.g + 1;
                    ownVisited.put(child.state, child);
                    nextLevel.add(child);
                    totalNodes++;

                    Node other = otherVisited.get(child.state);
                    if (other != null) {
                        // Hai phía gặp nhau tại child: giữ điểm gặp có tổng độ dài nhỏ nhất.
                        int total = child.g + other.g;
                        if (total < bestMeetTotal) {
                            bestMeetTotal = total;
                            meetFromStart = expandForward ? child : other;
                            meetFromGoal = expandForward ? other : child;
                        }
                    }
                }
            }
            if (expandForward) frontierForward = nextLevel;
            else frontierBackward = nextLevel;

            if (visitedForward.size() + visitedBackward.size() > MAX_VISITED_NODES) {
                error = "Bidirectional BFS đã đạt giới hạn bộ nhớ an toàn ("
                        + MAX_VISITED_NODES + " trạng thái).";
                time = System.currentTimeMillis() - startTime;
                return;
            }

            if (meetFromStart != null) {
                time = System.currentTimeMillis() - startTime;
                buildResult(meetFromStart, meetFromGoal);
                return;
            }
        }

        time = System.currentTimeMillis() - startTime;
        error = "Không tồn tại lời giải cho trạng thái hiện tại.";
    }

    /** Ghép nửa đường start -> gặp và nửa đường gặp -> goal thành lời giải đầy đủ. */
    private void buildResult(Node meetFromStart, Node meetFromGoal) {
        List<int[]> firstHalf = new ArrayList<>();
        Node cursor = meetFromStart;
        while (cursor != null) {
            firstHalf.add(cursor.state.value.clone());
            cursor = cursor.parent;
        }
        // Truy vết parent đi từ điểm gặp về start nên phải đảo lại thành start -> điểm gặp.
        java.util.Collections.reverse(firstHalf);
        List<int[]> secondHalf = new ArrayList<>();
        cursor = meetFromGoal;
        while (cursor != null) {
            secondHalf.add(cursor.state.value.clone());
            cursor = cursor.parent;
        }
        RESULT.addAll(firstHalf);
        // secondHalf được truy vết từ điểm gặp về goal theo parent (gần đích dần),
        // nên ghép xuôi từ phần tử thứ hai (phần tử đầu là điểm gặp đã có ở firstHalf).
        for (int i = 1; i < secondHalf.size(); i++) {
            RESULT.add(secondHalf.get(i));
        }
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
