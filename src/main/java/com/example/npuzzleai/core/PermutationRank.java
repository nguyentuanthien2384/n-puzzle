package com.example.npuzzleai.core;

/**
 * Đánh số hoán vị theo hệ Lehmer (thứ tự từ điển) cho bảng nhỏ (n &lt;= 12 ô).
 * Dùng cho bảng khoảng cách chính xác 2x2/3x3 trong bộ kiểm định heuristic.
 */
public final class PermutationRank {
    private PermutationRank() {
    }

    public static int factorial(int n) {
        if (n > 12) throw new IllegalArgumentException("n! vượt quá int với n = " + n);
        int f = 1;
        for (int i = 2; i <= n; i++) f *= i;
        return f;
    }

    public static int rank(Board b) {
        int n = b.cellCount();
        int rank = 0;
        int used = 0;
        for (int i = 0; i < n; i++) {
            int p = b.tileAt(i);
            int smallerUnused = p - Integer.bitCount(used & ((1 << p) - 1));
            rank = rank * (n - i) + smallerUnused;
            used |= 1 << p;
        }
        return rank;
    }

    public static int rank(int[] perm) {
        int n = perm.length;
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

    public static void unrank(int rank, int n, int[] out) {
        int[] digits = new int[n];
        for (int i = n - 1; i >= 0; i--) {
            digits[i] = rank % (n - i);
            rank /= (n - i);
        }
        int used = 0;
        for (int i = 0; i < n; i++) {
            int c = digits[i];
            for (int v = 0; v < n; v++) {
                if ((used & (1 << v)) != 0) continue;
                if (c == 0) {
                    out[i] = v;
                    used |= 1 << v;
                    break;
                }
                c--;
            }
        }
    }
}
