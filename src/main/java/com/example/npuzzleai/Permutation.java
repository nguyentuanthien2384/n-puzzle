package com.example.npuzzleai;

/**
 * Tiện ích mã hoá hoán vị dùng chung cho Pattern Database và Value Iteration.
 *
 * <p>Rank theo hệ giai thừa (Lehmer) giúp đánh số liên tục các hoán vị từ 0 đến n!-1,
 * và đánh số một phần cho bộ k vị trí chọn từ `cells` ô (dùng cho Pattern Database).</p>
 */
final class Permutation {
    private Permutation() {
    }

    /** Thứ tự từ điển của hoán vị n phần tử (n <= 9), kết quả trong [0, n!). */
    static int rank(int[] perm, int n) {
        int rank = 0;
        int used = 0;
        for (int i = 0; i < n; i++) {
            int p = perm[i];
            int smallerUnused = p - Integer.bitCount(used & ((1 << p) - 1));
            rank = rank * (n - i) + smallerUnused;
            used |= 1 << p;
        }
        return rank;
    }

    /** Giải mã rank thành hoán vị n phần tử ghi vào mảng out. */
    static void unrank(int rank, int n, int[] out) {
        int[] digits = new int[n];
        for (int i = n - 1; i >= 0; i--) {
            digits[i] = rank % (n - i);
            rank /= (n - i);
        }
        boolean[] used = new boolean[n];
        for (int i = 0; i < n; i++) {
            int c = digits[i];
            for (int v = 0; v < n; v++) {
                if (used[v]) continue;
                if (c == 0) {
                    out[i] = v;
                    used[v] = true;
                    break;
                }
                c--;
            }
        }
    }

    /**
     * Thứ tự của bộ k vị trí phân biệt chọn từ `cells` ô, kết quả trong [0, P(cells, k)).
     * Dùng để đánh chỉ số bảng Pattern Database theo vị trí các ô Pattern.
     */
    static int rankPositions(int[] pos, int k, int cells) {
        int rank = 0;
        int used = 0;
        for (int i = 0; i < k; i++) {
            int p = pos[i];
            int smallerUnused = p - Integer.bitCount(used & ((1 << p) - 1));
            rank = rank * (cells - i) + smallerUnused;
            used |= 1 << p;
        }
        return rank;
    }
}
