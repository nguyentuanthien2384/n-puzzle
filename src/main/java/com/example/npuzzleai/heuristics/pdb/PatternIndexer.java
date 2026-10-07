package com.example.npuzzleai.heuristics.pdb;

/**
 * Đánh chỉ số hoàn hảo (perfect hashing) cho bộ k vị trí phân biệt chọn từ {@code cells} ô.
 *
 * <p>Chỉ số là số hệ cơ số hỗn hợp (cells, cells-1, ..., cells-k+1): chữ số thứ i là số ô
 * nhỏ hơn pos[i] chưa bị dùng. Kết quả liên tục trong [0, P(cells, k)) nên bảng PDB là
 * một mảng byte không lỗ hổng.</p>
 */
public final class PatternIndexer {
    private PatternIndexer() {
    }

    /** P(cells, k) = cells! / (cells - k)!. */
    public static long count(int cells, int k) {
        long c = 1;
        for (int i = 0; i < k; i++) c *= (cells - i);
        return c;
    }

    public static int rank(int[] pos, int k, int cells) {
        long used = 0;
        int r = 0;
        for (int i = 0; i < k; i++) {
            int p = pos[i];
            int smaller = p - Long.bitCount(used & ((1L << p) - 1));
            r = r * (cells - i) + smaller;
            used |= 1L << p;
        }
        return r;
    }

    public static void unrank(int rank, int k, int cells, int[] out) {
        int[] digits = new int[k];
        for (int i = k - 1; i >= 0; i--) {
            int radix = cells - i;
            digits[i] = rank % radix;
            rank /= radix;
        }
        long used = 0;
        for (int i = 0; i < k; i++) {
            int c = digits[i];
            for (int v = 0; v < cells; v++) {
                if ((used & (1L << v)) != 0) continue;
                if (c == 0) {
                    out[i] = v;
                    used |= 1L << v;
                    break;
                }
                c--;
            }
        }
    }
}
