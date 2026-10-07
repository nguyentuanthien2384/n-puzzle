package com.example.npuzzleai.heuristics.pdb;

import com.example.npuzzleai.core.Goal;

import java.util.Arrays;

/**
 * Dựng PDB bằng 0-1 BFS ngược từ đích trong không gian trừu tượng (vị trí k ô pattern + ô trống).
 *
 * <p>Ô trống đi qua ô không thuộc pattern tốn 0 (không tính), đổi chỗ với ô pattern tốn 1.
 * Sau khi BFS xong, bảng được nén về chỉ số theo k vị trí ô pattern, lấy min theo mọi vị trí
 * ô trống. Đây là mô hình chi phí "pattern-moves" của Korf-Felner cho phép cộng các PDB rời nhau.</p>
 *
 * <p>Không gian trạng thái có P(n, k+1) phần tử - ví dụ 4x4 với pattern 5 ô: 5,77 triệu;
 * 5x5 với pattern 4 ô: 6,38 triệu. Builder từ chối không gian quá {@link #MAX_STATES}.</p>
 */
public final class PatternDatabaseBuilder {
    public static final String VERSION = "0-1bfs-v1";
    public static final long MAX_STATES = 400_000_000L;

    private PatternDatabaseBuilder() {
    }

    public static PatternDatabase build(PatternDefinition pattern, Goal goal) {
        if (pattern.size() != goal.size()) {
            throw new IllegalArgumentException("Pattern và đích khác kích thước");
        }
        long startMs = System.currentTimeMillis();
        int size = goal.size();
        int n = size * size;
        int k = pattern.tileCount();
        long space = PatternIndexer.count(n, k + 1);
        if (space > MAX_STATES || space > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("Pattern " + k + " ô trên bảng " + size + "x" + size
                    + " cần " + space + " trạng thái - quá lớn");
        }

        byte[] dist = new byte[(int) space];
        Arrays.fill(dist, PatternDatabase.UNREACHED);
        byte[] table = new byte[(int) PatternIndexer.count(n, k)];
        Arrays.fill(table, PatternDatabase.UNREACHED);

        int[] cur = new int[k + 1];
        int[] next = new int[k + 1];
        for (int i = 0; i < k; i++) cur[i] = goal.positionOf(pattern.tileAt(i));
        cur[k] = goal.blankPosition();
        int startState = PatternIndexer.rank(cur, k + 1, n);
        dist[startState] = 0;

        IntDeque deque = new IntDeque(1 << 16);
        deque.addLast(startState);
        while (!deque.isEmpty()) {
            int s = deque.pollFirst();
            int d = dist[s];
            PatternIndexer.unrank(s, k + 1, n, cur);
            int tableIndex = PatternIndexer.rank(cur, k, n);
            if (d < table[tableIndex]) table[tableIndex] = (byte) d;

            int blank = cur[k];
            int row = blank / size, col = blank % size;
            for (int dir = 0; dir < 4; dir++) {
                int tr = row + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
                int tc = col + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                if (tr < 0 || tr >= size || tc < 0 || tc >= size) continue;
                int target = tr * size + tc;
                System.arraycopy(cur, 0, next, 0, k + 1);
                next[k] = target;
                int nd = d;
                for (int t = 0; t < k; t++) {
                    if (cur[t] == target) {
                        next[t] = blank; // ô pattern trượt vào chỗ ô trống cũ
                        nd = d + 1;
                        break;
                    }
                }
                int ns = PatternIndexer.rank(next, k + 1, n);
                if (nd < dist[ns]) {
                    dist[ns] = (byte) nd;
                    if (nd == d) deque.addFirst(ns);
                    else deque.addLast(ns);
                }
            }
        }

        long checksum = PatternDatabase.checksum(table);
        PdbMetadata meta = new PdbMetadata(size, goal.board().toArray(), pattern.tiles(),
                PdbMetadata.COST_MODEL, VERSION, table.length, checksum, System.currentTimeMillis() - startMs);
        return new PatternDatabase(meta, table);
    }

    /** Hàng đợi hai đầu số nguyên, tự giãn - cho 0-1 BFS. */
    private static final class IntDeque {
        private int[] buf;
        private int head;
        private int size;

        IntDeque(int capacity) {
            buf = new int[capacity];
        }

        void addFirst(int v) {
            if (size == buf.length) grow();
            head = (head - 1 + buf.length) % buf.length;
            buf[head] = v;
            size++;
        }

        void addLast(int v) {
            if (size == buf.length) grow();
            buf[(head + size) % buf.length] = v;
            size++;
        }

        int pollFirst() {
            int v = buf[head];
            head = (head + 1) % buf.length;
            size--;
            return v;
        }

        boolean isEmpty() {
            return size == 0;
        }

        private void grow() {
            int[] bigger = new int[buf.length * 2];
            for (int i = 0; i < size; i++) bigger[i] = buf[(head + i) % buf.length];
            buf = bigger;
            head = 0;
        }
    }
}
