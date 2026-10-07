package com.example.npuzzleai;

import java.util.Arrays;
import java.util.Vector;

/** Tiện ích chung cho đường đi lời giải của các thuật toán tìm kiếm cục bộ. */
final class Paths {
    private Paths() {
    }

    /**
     * Rút gọn đường đi bằng cách xoá các cặp nước đi trái chiều liền kề
     * (trạng thái lặp lại cách nhau một bước), lặp đến khi không còn rút gọn được.
     */
    static void compress(Vector<int[]> path) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 2 < path.size(); i++) {
                if (Arrays.equals(path.get(i), path.get(i + 2))) {
                    path.remove(i + 1);
                    path.remove(i + 1);
                    changed = true;
                    i = Math.max(-1, i - 2);
                }
            }
        }
    }

    /** Hai trạng thái kế tiếp nhau chỉ khác một nước đi hợp lệ (đổi chỗ ô trống với ô kề). */
    static boolean validStep(int[] a, int[] b) {
        int size = (int) Math.round(Math.sqrt(a.length));
        int p = -1, q = -1;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                if (p < 0) p = i;
                else if (q < 0) q = i;
                else return false;
            }
        }
        if (p < 0 || q < 0) return false; // trùng nhau hoặc khác hơn 2 ô
        if (a[p] != 0 && a[q] != 0) return false; // không có ô trống trong cặp đổi chỗ
        if (a[q] == 0) { // chuẩn hoá: p là vị trí ô trống ở trạng thái a
            int t = p;
            p = q;
            q = t;
        }
        if (a[p] != 0 || b[q] != 0 || b[p] != a[q]) return false;
        return Math.abs(p - q) == size || (Math.abs(p - q) == 1 && p / size == q / size);
    }
}
