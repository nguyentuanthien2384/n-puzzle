package com.example.npuzzleai.core;

/**
 * Kiểm tra khả giải tổng quát cho đích bất kỳ.
 *
 * <p>Mỗi nước đi là một phép đổi chỗ (ô trống với ô kề), nên đồng thời đảo tính
 * chẵn lẻ của hoán vị ô và tính chẵn lẻ khoảng cách Manhattan của ô trống tới vị trí
 * đích. Vì vậy bất biến parity(hoán vị start -&gt; goal) == parity(khoảng cách ô trống)
 * là điều kiện cần; định lý cổ điển cho N-Puzzle (N &gt;= 2) khẳng định nó cũng là điều kiện đủ.
 * Cách này không phụ thuộc kích thước chẵn/lẻ hay vị trí ô trống của đích.</p>
 */
public final class Solvability {
    private Solvability() {
    }

    public static boolean isSolvable(Board start, Goal goal) {
        if (start.size() != goal.size()) return false;
        int n = start.cellCount();
        int size = start.size();

        // perm[i] = vị trí đích của ô đang nằm tại i (tính cả ô trống).
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = goal.positionOf(start.tileAt(i));

        boolean[] visited = new boolean[n];
        int cycles = 0;
        for (int i = 0; i < n; i++) {
            if (visited[i]) continue;
            cycles++;
            int j = i;
            while (!visited[j]) {
                visited[j] = true;
                j = perm[j];
            }
        }
        int permutationParity = (n - cycles) & 1;

        int b = start.blankIndex();
        int gb = goal.blankPosition();
        int blankDistance = Math.abs(b / size - gb / size) + Math.abs(b % size - gb % size);
        return permutationParity == (blankDistance & 1);
    }

    public static boolean isSolvable(PuzzleProblem problem) {
        return isSolvable(problem.start(), problem.goal());
    }
}
