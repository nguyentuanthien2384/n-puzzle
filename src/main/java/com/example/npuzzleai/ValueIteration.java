package com.example.npuzzleai;

import java.util.Arrays;
import java.util.Vector;

/**
 * Value Iteration - lặp giá trị trên MDP (Russell &amp; Norvig, chương "Quy hoạch động").
 *
 * <p>Mô hình hoá N-Puzzle như một MDP tất định (deterministic MDP): mỗi hành động
 * đưa ô trống sang một ô kề, chi phí mỗi bước là 1, phần thưởng 0 trừ khi tới đích.
 * Phương trình Bellman tối ưu:
 *
 * <pre>V*(s) = 0 nếu s là đích; ngược lại V*(s) = 1 + min_a V*(s')</pre>
 *
 * <p>Lặp cập nhật V cho tới hội tụ rồi trích chính sách tham lam: tại mỗi trạng thái
 * đi tới trạng thái kế tiếp có V nhỏ nhất. Chính sách thu được tối ưu vì chi phí bước
 * đều bằng nhau. Vì bảng 4x4 có 16! ~ 2.10^13 trạng thái nên Value Iteration chỉ
 * chạy với bảng 3x3 (9! = 362.880 hoán vị).</p>
 */
public class ValueIteration {
    public Node startNode;
    public Node goalNode;
    public final Vector<int[]> RESULT = new Vector<>();
    public int approvedNodes;   // số lần cập nhật Bellman
    public int totalNodes;      // kích thước không gian trạng thái
    public long time;
    public String error;
    public static volatile boolean stop = false;

    private static final long TIME_LIMIT_MS = 60_000L;
    private static final int MAX_ITERATIONS = 100;

    public void solve() {
        reset();
        if (!validateInput()) return;

        long startTime = System.currentTimeMillis();
        int n = startNode.state.getLength();
        int space = 1;
        for (int i = 2; i <= n; i++) space *= i;
        totalNodes = space;

        int[] goalArr = goalNode.state.value.clone();
        int goalRank = Permutation.rank(goalArr, n);
        int startRank = Permutation.rank(startNode.state.value, n);

        // 1) Quét toàn bộ không gian từ đích để xác định vùng khả giải và danh sách kế tiếp.
        //    Nước đi đối xứng nên tập kế tiếp tiến và lùi là một.
        boolean[] reachable = new boolean[space];
        int[][] successors = new int[space][];
        reachable[goalRank] = true;
        int[] queue = new int[space];
        int head = 0, tail = 0;
        queue[tail++] = goalRank;

        int[] current = new int[n];
        int[] child = new int[n];
        int size = startNode.state.getSize();
        while (head < tail) {
            if (cancelled(startTime)) return;
            int r = queue[head++];
            Permutation.unrank(r, n, current);
            int blank = 0;
            while (current[blank] != 0) blank++;
            int row = blank / size, col = blank % size;

            int count = 0;
            int[] neighborRanks = new int[4];
            for (int dir = 0; dir < 4; dir++) {
                int tr = row + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
                int tc = col + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                if (tr < 0 || tr >= size || tc < 0 || tc >= size) continue;
                int target = tr * size + tc;
                System.arraycopy(current, 0, child, 0, n);
                child[blank] = child[target];
                child[target] = 0;
                int childRank = Permutation.rank(child, n);
                neighborRanks[count++] = childRank;
                if (!reachable[childRank]) {
                    reachable[childRank] = true;
                    queue[tail++] = childRank;
                }
            }
            successors[r] = Arrays.copyOf(neighborRanks, count);
        }

        if (!reachable[startRank]) {
            time = System.currentTimeMillis() - startTime;
            error = "Trạng thái hiện tại không thể biến đổi về trạng thái đích đã chọn.";
            return;
        }

        // 2) Lặp giá trị: V(s) = 1 + min V(s') cho tới khi hội tụ.
        short[] value = new short[space];
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            if (cancelled(startTime)) return;
            boolean changed = false;
            for (int r = 0; r < space; r++) {
                if (r == goalRank || !reachable[r]) continue;
                int best = Short.MAX_VALUE;
                for (int next : successors[r]) {
                    if (value[next] < best) best = value[next];
                }
                short updated = (short) (best + 1);
                if (updated != value[r]) {
                    value[r] = updated;
                    changed = true;
                    approvedNodes++;
                }
            }
            if (!changed) break;
        }

        // 3) Trích chính sách tham lam: luôn đi tới trạng thái kế tiếp có V nhỏ nhất.
        RESULT.add(startNode.state.value.clone());
        int cursor = startRank;
        int[] frame = new int[n];
        boolean[] onPath = new boolean[space];
        while (cursor != goalRank) {
            onPath[cursor] = true;
            int best = Short.MAX_VALUE;
            int bestRank = -1;
            for (int next : successors[cursor]) {
                if (value[next] < best) {
                    best = value[next];
                    bestRank = next;
                }
            }
            if (bestRank < 0 || best >= value[cursor] || onPath[bestRank]) {
                time = System.currentTimeMillis() - startTime;
                error = "Chính sách trích xuất không hội tụ về đích.";
                return;
            }
            cursor = bestRank;
            Permutation.unrank(cursor, n, frame);
            RESULT.add(frame.clone());
        }

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
            error = "Value Iteration chỉ phù hợp cho bảng 3x3 vì không gian trạng thái 4x4 (16!) quá lớn.";
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
