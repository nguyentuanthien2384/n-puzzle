package com.example.npuzzleai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Heuristic Pattern Database (Korf &amp; Felner 2002).
 *
 * <p>Ý tưởng: chia các ô số thành các nhóm Pattern rời rạc, dựng trước bảng khoảng cách
 * cho từng nhóm trong "không gian Pattern" (chỉ các ô thuộc nhóm được tính chi phí,
 * ô còn lại xem như chỗ trống). Tổng khoảng cách của các nhóm rời rạc là một
 * lower bound chấp nhận được của số bước tối ưu.</p>
 *
 * <ul>
 *   <li>Bảng 3x3: bảng đầy đủ toàn bộ 9! hoán vị => heuristic chính xác (bằng khoảng cách tối ưu).</li>
 *   <li>Bảng 4x4: phân hoạch 5-5-5, mỗi Pattern có 16*15*14*13*12*11 ~ 5,77 triệu trạng thái,
 *       dựng bằng 0-1 BFS (đi ô thường chi phí 0, đi ô Pattern chi phí 1).</li>
 * </ul>
 */
public final class PatternDatabase {
    private static final byte INF = 0x7F;
    private static final int PATTERN5_TABLE_SIZE = 16 * 15 * 14 * 13 * 12; // 524160

    /** Nhóm Pattern cho đích kiểu 1 (số 0 nằm góc trên trái). */
    private static final int[][] PATTERNS_GOAL1 = {
            {1, 2, 4, 5, 6}, {3, 7, 10, 11, 14}, {8, 9, 12, 13, 15}
    };
    /** Nhóm Pattern cho đích kiểu 2 (số 0 nằm góc dưới phải). */
    private static final int[][] PATTERNS_GOAL2 = {
            {1, 2, 5, 6, 9}, {3, 4, 7, 8, 11}, {10, 12, 13, 14, 15}
    };

    private static final Map<String, int[][]> PATTERN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, byte[][]> TABLE_CACHE = new ConcurrentHashMap<>();

    private PatternDatabase() {
    }

    /** Chủ động dựng bảng (được gọi trước khi tìm kiếm để không tính vào thời gian dừng). */
    public static void ensureBuilt(int size, State goalState) {
        buildIfNeeded(size, goalState);
    }

    /** Giá trị heuristic Pattern Database của một trạng thái. */
    public static int lookup(State state, State goalState) {
        int size = state.getSize();
        String key = cacheKey(size, goalState);
        buildIfNeeded(size, goalState);

        if (size <= 3) {
            int v = TABLE_CACHE.get(key)[0][Permutation.rank(state.value, size * size)];
            return v < 0 ? 0 : v;
        }

        int[] tilePos = new int[state.getLength()];
        for (int i = 0; i < state.getLength(); i++) {
            tilePos[state.value[i]] = i;
        }
        int[][] patterns = PATTERN_CACHE.get(key);
        byte[][] tables = TABLE_CACHE.get(key);
        int h = 0;
        for (int p = 0; p < patterns.length; p++) {
            int v = tables[p][Permutation.rankPositions(tilePosOf(patterns[p], tilePos), patterns[p].length, size * size)];
            if (v < INF) h += v;
        }
        return h;
    }

    private static int[] tilePosOf(int[] pattern, int[] tilePos) {
        int[] result = new int[pattern.length];
        for (int i = 0; i < pattern.length; i++) {
            result[i] = tilePos[pattern[i]];
        }
        return result;
    }

    private static String cacheKey(int size, State goalState) {
        return size + ":" + Arrays.hashCode(goalState.value);
    }

    private static void buildIfNeeded(int size, State goalState) {
        String key = cacheKey(size, goalState);
        if (TABLE_CACHE.containsKey(key)) return;
        synchronized (PatternDatabase.class) {
            if (TABLE_CACHE.containsKey(key)) return;
            if (size <= 3) {
                byte[][] tables = new byte[1][];
                tables[0] = buildFullTable(size * size, goalState.value);
                PATTERN_CACHE.put(key, new int[0][]);
                TABLE_CACHE.put(key, tables);
            } else if (size == 4) {
                int[][] patterns = patternsFor(goalState);
                byte[][] tables = new byte[patterns.length][];
                for (int p = 0; p < patterns.length; p++) {
                    tables[p] = buildPatternTable(patterns[p], goalState);
                }
                PATTERN_CACHE.put(key, patterns);
                TABLE_CACHE.put(key, tables);
            } else {
                throw new IllegalStateException("Pattern Database chỉ hỗ trợ bảng 3x3 và 4x4.");
            }
        }
    }

    private static int[][] patternsFor(State goalState) {
        int[] g = goalState.value;
        if (g[0] == 0 && g[15] == 15) return PATTERNS_GOAL1;
        if (g[0] == 1 && g[15] == 0) return PATTERNS_GOAL2;
        // Đích tuỳ ý: chia ba khối theo vị trí đích của các ô.
        List<int[]> groups = new ArrayList<>();
        int[] current = new int[5];
        int count = 0;
        for (int goalIndex = 0; goalIndex < 16; goalIndex++) {
            int tile = g[goalIndex];
            if (tile == 0) continue;
            current[count++] = tile;
            if (count == 5) {
                groups.add(current.clone());
                count = 0;
            }
        }
        return groups.toArray(new int[0][]);
    }

    /**
     * Bảng đầy đủ cho bảng nhỏ (n ô): BFS từ đích trên toàn bộ hoán vị,
     * cho khoảng cách chính xác. Ô không tới được ghi -1.
     */
    private static byte[] buildFullTable(int n, int[] goalValue) {
        int space = 1;
        for (int i = 2; i <= n; i++) space *= i;
        byte[] dist = new byte[space];
        Arrays.fill(dist, (byte) -1);

        int[] goalArr = goalValue.clone();
        int goalRank = Permutation.rank(goalArr, n);
        dist[goalRank] = 0;

        int[] queue = new int[space];
        int head = 0, tail = 0;
        queue[tail++] = goalRank;

        int[] current = new int[n];
        int[] child = new int[n];
        int size = (int) Math.round(Math.sqrt(n));
        while (head < tail) {
            int r = queue[head++];
            int d = dist[r];
            Permutation.unrank(r, n, current);
            int blank = 0;
            while (current[blank] != 0) blank++;
            int row = blank / size, col = blank % size;
            for (int dir = 0; dir < 4; dir++) {
                int tr = row + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
                int tc = col + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                if (tr < 0 || tr >= size || tc < 0 || tc >= size) continue;
                int target = tr * size + tc;
                System.arraycopy(current, 0, child, 0, n);
                child[blank] = child[target];
                child[target] = 0;
                int childRank = Permutation.rank(child, n);
                if (dist[childRank] == -1) {
                    dist[childRank] = (byte) (d + 1);
                    queue[tail++] = childRank;
                }
            }
        }
        return dist;
    }

    /**
     * Dựng bảng cho một Pattern 5 ô trên bảng 4x4 bằng 0-1 BFS ngược từ đích.
     * Không gian trạng thái gồm vị trí 5 ô Pattern + ô trống, mã hoá cơ số 16
     * thành số 24 bit (mỗi vị trí 4 bit) => tra cứu mảng byte 2^24 phần tử.
     */
    private static byte[] buildPatternTable(int[] pattern, State goalState) {
        byte[] dist = new byte[1 << 24];
        Arrays.fill(dist, INF);

        int[] goalPos = new int[16];
        for (int i = 0; i < 16; i++) {
            goalPos[goalState.value[i]] = i;
        }

        int startState = 0;
        for (int t = 0; t < pattern.length; t++) {
            startState |= goalPos[pattern[t]] << (4 * t);
        }
        startState |= goalPos[0] << (4 * pattern.length);

        byte[] table = new byte[PATTERN5_TABLE_SIZE];
        Arrays.fill(table, INF);
        IntDeque deque = new IntDeque(1 << 20);
        dist[startState] = 0;
        deque.addLast(startState);

        while (!deque.isEmpty()) {
            int s = deque.pollFirst();
            int d = dist[s];

            int[] pos = new int[pattern.length + 1];
            for (int t = 0; t <= pattern.length; t++) {
                pos[t] = (s >>> (4 * t)) & 15;
            }
            int blank = pos[pattern.length];

            int tableIndex = Permutation.rankPositions(pos, pattern.length, 16);
            if (d < table[tableIndex]) table[tableIndex] = (byte) d;

            int row = blank / 4, col = blank % 4;
            for (int dir = 0; dir < 4; dir++) {
                int tr = row + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
                int tc = col + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                if (tr < 0 || tr >= 4 || tc < 0 || tc >= 4) continue;
                int target = tr * 4 + tc;

                int movingTile = -1;
                for (int t = 0; t < pattern.length; t++) {
                    if (pos[t] == target) {
                        movingTile = t;
                        break;
                    }
                }

                int nextState;
                int nextDist;
                if (movingTile >= 0) {
                    // Ô trống đổi chỗ với ô Pattern (chi phí 1): ô Pattern chuyển đến
                    // vị trí cũ của ô trống, ô trống chuyển đến ô kề `target`.
                    nextState = s + ((blank - pos[movingTile]) << (4 * movingTile))
                            + ((target - blank) << (4 * pattern.length));
                    nextDist = d + 1;
                } else {
                    // Ô trống đi qua ô thường (chi phí 0).
                    nextState = s + ((target - blank) << (4 * pattern.length));
                    nextDist = d;
                }
                if (nextDist < dist[nextState]) {
                    dist[nextState] = (byte) nextDist;
                    if (nextDist == d) deque.addFirst(nextState);
                    else deque.addLast(nextState);
                }
            }
        }
        return table;
    }

    /** Hàng đợi hai đầu cho số nguyên - dùng bởi 0-1 BFS. */
    private static final class IntDeque {
        private int[] buf;
        private int head;
        private int size;

        IntDeque(int capacity) {
            buf = new int[Math.max(4, capacity)];
        }

        void addFirst(int value) {
            if (size == buf.length) grow();
            head = (head - 1 + buf.length) % buf.length;
            buf[head] = value;
            size++;
        }

        void addLast(int value) {
            if (size == buf.length) grow();
            buf[(head + size) % buf.length] = value;
            size++;
        }

        int pollFirst() {
            int value = buf[head];
            head = (head + 1) % buf.length;
            size--;
            return value;
        }

        boolean isEmpty() {
            return size == 0;
        }

        private void grow() {
            int[] next = new int[buf.length * 2];
            for (int i = 0; i < size; i++) {
                next[i] = buf[(head + i) % buf.length];
            }
            buf = next;
            head = 0;
        }
    }
}
