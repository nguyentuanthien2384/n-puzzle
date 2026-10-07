package com.example.npuzzleai.core;

import java.util.Random;

/**
 * Sinh trạng thái có seed để thí nghiệm tái lập được.
 *
 * <ul>
 *   <li>{@link #randomWalk}: đi ngẫu nhiên từ đích - luôn khả giải, độ khó tăng theo số bước.</li>
 *   <li>{@link #uniformSolvable}: hoán vị ngẫu nhiên đều trên toàn bộ lớp khả giải.</li>
 *   <li>{@link #unsolvable}: trạng thái khả giải rồi đổi chỗ hai ô số - chắc chắn vô nghiệm.</li>
 * </ul>
 */
public final class BoardGenerator {
    private BoardGenerator() {
    }

    public static Board randomWalk(Goal goal, int steps, Random random) {
        Board b = goal.board();
        Move previous = null;
        Move[] candidates = new Move[Move.COUNT];
        for (int s = 0; s < steps; s++) {
            int count = 0;
            for (int d = 0; d < Move.COUNT; d++) {
                Move m = Move.byOrdinal(d);
                if (b.canMove(m) && (previous == null || m != previous.opposite())) candidates[count++] = m;
            }
            Move chosen = candidates[random.nextInt(count)];
            b = b.move(chosen);
            previous = chosen;
        }
        return b;
    }

    public static Board uniformSolvable(Goal goal, Random random) {
        int size = goal.size();
        int n = size * size;
        int[] v = goal.board().toArray();
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = v[i];
            v[i] = v[j];
            v[j] = t;
        }
        Board b = Board.of(size, v);
        if (!Solvability.isSolvable(b, goal)) b = swapTwoTiles(b);
        return b;
    }

    public static Board unsolvable(Goal goal, Random random) {
        return swapTwoTiles(uniformSolvable(goal, random));
    }

    /** Đổi chỗ hai ô số đầu tiên (không phải ô trống) - đảo lớp khả giải. */
    public static Board swapTwoTiles(Board b) {
        int[] v = b.toArray();
        int first = -1;
        for (int i = 0; i < v.length; i++) {
            if (v[i] == 0) continue;
            if (first < 0) {
                first = i;
            } else {
                int t = v[first];
                v[first] = v[i];
                v[i] = t;
                break;
            }
        }
        return Board.of(b.size(), v);
    }
}
