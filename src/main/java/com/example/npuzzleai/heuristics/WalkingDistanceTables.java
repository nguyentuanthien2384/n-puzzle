package com.example.npuzzleai.heuristics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bảng tra Walking Distance (Ken'ichiro Takahashi) dùng chung cho {@link WalkingDistanceHeuristic}
 * và lớp tương thích {@code com.example.npuzzleai.WalkingDistance} của giao diện cũ.
 *
 * <p>Không gian rút gọn: ma trận đếm c[i][j] = số ô đang ở hàng i có hàng đích j (bỏ qua cột).
 * Một nước đi dọc đưa ô trống đổi hàng với một ô tuỳ ý; bảng khoảng cách dựng một lần bằng BFS
 * từ ma trận đích rồi tra O(1). Thành phần ngang dùng cùng bảng nhờ tính đối xứng. Lớp đích chứa
 * ô trống chỉ có (kích thước - 1) ô nên bảng phụ thuộc hàng/cột đích của ô trống.</p>
 *
 * <p>Đặt trong gói {@code heuristics} để search engine không phụ thuộc ngược vào gói giao diện cũ.</p>
 */
public final class WalkingDistanceTables {
    /** Bảng tra theo (kích thước bảng, chỉ số hàng/cột đích của ô trống). */
    private static final Map<Long, LongByteTable> TABLES = new ConcurrentHashMap<>();

    private WalkingDistanceTables() {
    }

    /**
     * Lấy (và dựng nếu chưa có) bảng cho kích thước và lớp đích của ô trống.
     * ConcurrentHashMap thay cho synchronized để benchmark song song không tranh khoá mỗi lần tra.
     */
    public static LongByteTable tableFor(int size, int blankGoalLine) {
        long key = ((long) size << 8) | blankGoalLine;
        return TABLES.computeIfAbsent(key, k -> buildTable(size, blankGoalLine));
    }

    /**
     * Dựng bảng bằng BFS trong không gian ma trận đếm c[i][j] = số ô ở hàng i
     * thuộc hàng đích j. Lớp đích của ô trống (blankGoalLine) chỉ có size-1 ô.
     * Hàng trống suy ra được từ ma trận nên khoá chỉ cần các cột 0..size-2
     * cùng chỉ số hàng trống.
     */
    private static LongByteTable buildTable(int size, int blankGoalLine) {
        int expected = switch (size) {
            case 3 -> 2_000;
            case 4 -> 30_000;
            default -> 3_000_000;
        };
        LongByteTable dist = new LongByteTable(expected);
        LongQueue queue = new LongQueue(1 << 12);

        byte[][] goal = new byte[size][size];
        for (int i = 0; i < size; i++) {
            goal[i][i] = (byte) size;
        }
        goal[blankGoalLine][blankGoalLine] = (byte) (size - 1);

        long goalKey = pack(goal, blankGoalLine, size);
        dist.put(goalKey, (byte) 0);
        queue.add(goalKey);

        byte[][] matrix = new byte[size][size];
        while (!queue.isEmpty()) {
            long key = queue.poll();
            int d = dist.get(key);
            int blankLine = decode(key, size, matrix);
            for (int rp = blankLine - 1; rp <= blankLine + 1; rp += 2) {
                if (rp < 0 || rp >= size) continue;
                for (int j = 0; j < size; j++) {
                    if (matrix[rp][j] == 0) continue;
                    matrix[rp][j]--;
                    matrix[blankLine][j]++;
                    long next = pack(matrix, rp, size);
                    if (dist.get(next) < 0) {
                        dist.put(next, (byte) (d + 1));
                        queue.add(next);
                    }
                    matrix[rp][j]++;
                    matrix[blankLine][j]--;
                }
            }
        }
        return dist;
    }

    /** Nén ma trận đếm thành khoá 64 bit: 3 bit/cột đầu cho mỗi hàng + 3 bit hàng trống. */
    public static long pack(byte[][] matrix, int blankLine, int size) {
        long key = blankLine;
        int shift = 3;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size - 1; j++, shift += 3) {
                key |= ((long) matrix[i][j]) << shift;
            }
        }
        return key;
    }

    /** Giải mã khoá thành ma trận (cột cuối suy ra từ tổng hàng); trả về hàng trống. */
    private static int decode(long key, int size, byte[][] matrix) {
        int blankLine = (int) (key & 7);
        int shift = 3;
        for (int i = 0; i < size; i++) {
            int sum = 0;
            for (int j = 0; j < size - 1; j++, shift += 3) {
                matrix[i][j] = (byte) ((key >>> shift) & 7);
                sum += matrix[i][j];
            }
            int rowSum = (i == blankLine) ? size - 1 : size;
            matrix[i][size - 1] = (byte) (rowSum - sum);
        }
        return blankLine;
    }

    /**
     * Bảng băm long -> byte dùng địa chỉ mở (linear probing), thay cho HashMap
     * để giảm mạnh bộ nhớ khi bảng Walking Distance của 5x5 lên tới ~1,5 triệu trạng thái.
     * Giá trị -1 nghĩa là khoá chưa tồn tại (khoảng cách luôn >= 0).
     */
    public static final class LongByteTable {
        private final long[] keys;
        private final byte[] values;
        private final int mask;

        public LongByteTable(int expectedEntries) {
            int capacity = 16;
            while (capacity < expectedEntries * 2) capacity <<= 1;
            this.keys = new long[capacity];
            this.values = new byte[capacity];
            this.mask = capacity - 1;
        }

        private static int hash(long key) {
            key *= 0x9E3779B97F4A7C15L;
            key ^= key >>> 32;
            return (int) key;
        }

        public void put(long key, byte value) {
            int i = hash(key) & mask;
            while (keys[i] != 0 && keys[i] != key) i = (i + 1) & mask;
            keys[i] = key;
            values[i] = value;
        }

        public byte get(long key) {
            int i = hash(key) & mask;
            while (keys[i] != 0) {
                if (keys[i] == key) return values[i];
                i = (i + 1) & mask;
            }
            return -1;
        }
    }

    /** Hàng đợi long vòng, tự giãn - tránh autoboxing của ArrayDeque<Long>. */
    static final class LongQueue {
        private long[] buf;
        private int head;
        private int size;

        LongQueue(int capacity) {
            buf = new long[Math.max(4, capacity)];
        }

        void add(long value) {
            if (size == buf.length) grow();
            buf[(head + size) % buf.length] = value;
            size++;
        }

        long poll() {
            long value = buf[head];
            head = (head + 1) % buf.length;
            size--;
            return value;
        }

        boolean isEmpty() {
            return size == 0;
        }

        private void grow() {
            long[] next = new long[buf.length * 2];
            for (int i = 0; i < size; i++) {
                next[i] = buf[(head + i) % buf.length];
            }
            buf = next;
            head = 0;
        }
    }
}
