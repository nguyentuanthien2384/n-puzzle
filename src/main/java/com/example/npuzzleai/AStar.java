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

/** Thuật toán A* với PriorityQueue và gScore để tránh sinh lặp trạng thái. */
public class AStar {
    public Node startNode;
    public Node goalNode;
    public Node currentNode;
    public final Vector<Node> CLOSED = new Vector<>();
    public final Vector<int[]> RESULT = new Vector<>();
    protected int approvedNodes;
    protected int totalNodes;
    protected long time;
    protected static volatile boolean stop = false;
    protected String error;

    private static final long TIME_LIMIT_MS = 60_000L;

    private final PriorityQueue<Node> fringe = new PriorityQueue<>(
            Comparator.comparingInt((Node n) -> n.f)
                    .thenComparingInt(n -> n.h)
                    .thenComparingInt(n -> -n.g)
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
        Set<State> closedStates = new HashSet<>();

        startNode.parent = null;
        startNode.g = 0;
        startNode.h = startNode.estimate(goalNode.state);
        startNode.f = startNode.g + startNode.h;
        fringe.add(startNode);
        bestG.put(startNode.state, 0);

        while (!fringe.isEmpty()) {
            if (cancelled(startTime)) return;

            currentNode = fringe.poll();
            Integer bestKnown = bestG.get(currentNode.state);
            if (bestKnown == null || currentNode.g != bestKnown || closedStates.contains(currentNode.state)) {
                continue; // Bản ghi cũ/stale trong priority queue.
            }

            if (currentNode.equals(goalNode)) {
                time = System.currentTimeMillis() - startTime;
                totalNodes = bestG.size();
                addResult(currentNode);
                clearWorkCollections();
                return;
            }

            closedStates.add(currentNode.state);
            CLOSED.add(currentNode);
            approvedNodes++;

            for (Node child : currentNode.successors()) {
                int tentativeG = currentNode.g + child.cost;
                int knownG = bestG.getOrDefault(child.state, Integer.MAX_VALUE);
                if (tentativeG >= knownG) continue;

                child.parent = currentNode;
                child.g = tentativeG;
                child.h = child.estimate(goalNode.state);
                child.f = child.g + child.h;

                bestG.put(child.state, tentativeG);
                closedStates.remove(child.state); // Cho phép mở lại node nếu tìm thấy đường tốt hơn.
                fringe.add(child);
            }
        }

        time = System.currentTimeMillis() - startTime;
        totalNodes = bestG.size();
        error = "Không tồn tại lời giải cho trạng thái hiện tại.";
        clearWorkCollections();
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

    private boolean cancelled(long startTime) {
        if (stop || Thread.currentThread().isInterrupted()) {
            error = "Đã dừng tìm kiếm theo yêu cầu.";
            time = System.currentTimeMillis() - startTime;
            clearWorkCollections();
            return true;
        }
        if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
            error = "Thuật toán quá tốn thời gian (vượt quá 60 giây).";
            time = System.currentTimeMillis() - startTime;
            clearWorkCollections();
            return true;
        }
        return false;
    }

    private void reset() {
        RESULT.clear();
        fringe.clear();
        CLOSED.clear();
        currentNode = null;
        approvedNodes = 0;
        totalNodes = 0;
        time = 0;
        error = null;
    }

    private void clearWorkCollections() {
        fringe.clear();
        CLOSED.clear();
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
