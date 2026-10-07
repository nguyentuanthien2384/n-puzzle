package com.example.npuzzleai.heuristics.pdb;

import com.example.npuzzleai.core.Goal;

import java.util.ArrayList;
import java.util.List;

/**
 * Phân hoạch mặc định theo <i>vị trí đích</i> (ô gần nhau trong đích vào cùng pattern),
 * rồi ánh xạ sang số ô qua trạng thái đích - nên đúng với mọi đích.
 *
 * <ul>
 *   <li>2x2, 3x3: một pattern gồm mọi ô số - PDB chính xác (h = h*).</li>
 *   <li>4x4: 5-5-5 (Korf-Felner).</li>
 *   <li>5x5: 6 pattern 4 ô dạng khối 2x2 / cột - nhẹ (mỗi bảng 303.600 entry) nhưng mạnh hơn Manhattan.</li>
 * </ul>
 * Nhóm ô được định nghĩa cho đích có ô trống ở ô cuối; đích có ô trống ở ô 0 dùng ảnh đối xứng
 * tâm; đích khác thay ô chứa ô trống bằng ô cuối.
 */
public final class DefaultPartitions {
    private static final int[][] CELLS_4 = {
            {0, 1, 4, 5, 8}, {2, 3, 6, 7, 10}, {9, 11, 12, 13, 14}
    };
    private static final int[][] CELLS_5 = {
            {0, 1, 5, 6}, {2, 3, 7, 8}, {4, 9, 14, 19}, {10, 11, 15, 16}, {12, 13, 17, 18}, {20, 21, 22, 23}
    };

    private DefaultPartitions() {
    }

    public static boolean supports(int size) {
        return size >= 2 && size <= 5;
    }

    public static List<PatternDefinition> forGoal(Goal goal) {
        int size = goal.size();
        int n = size * size;
        int[][] cellGroups;
        if (size <= 3) {
            int[] all = new int[n - 1];
            for (int i = 0; i < n - 1; i++) all[i] = i;
            cellGroups = new int[][]{all};
        } else if (size == 4) {
            cellGroups = deepCopy(CELLS_4);
        } else if (size == 5) {
            cellGroups = deepCopy(CELLS_5);
        } else {
            throw new IllegalArgumentException("Chưa có phân hoạch mặc định cho bảng " + size + "x" + size);
        }

        int blank = goal.blankPosition();
        if (blank == 0) {
            for (int[] g : cellGroups) for (int i = 0; i < g.length; i++) g[i] = n - 1 - g[i];
        } else if (blank != n - 1) {
            for (int[] g : cellGroups) for (int i = 0; i < g.length; i++) if (g[i] == blank) g[i] = n - 1;
        }

        List<PatternDefinition> patterns = new ArrayList<>(cellGroups.length);
        for (int[] cells : cellGroups) {
            int[] tiles = new int[cells.length];
            for (int i = 0; i < cells.length; i++) tiles[i] = goal.board().tileAt(cells[i]);
            patterns.add(new PatternDefinition(size, tiles));
        }
        return patterns;
    }

    private static int[][] deepCopy(int[][] src) {
        int[][] copy = new int[src.length][];
        for (int i = 0; i < src.length; i++) copy[i] = src[i].clone();
        return copy;
    }
}
