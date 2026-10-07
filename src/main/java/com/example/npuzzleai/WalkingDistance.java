package com.example.npuzzleai;

import com.example.npuzzleai.heuristics.WalkingDistanceTables;

/**
 * Heuristic Walking Distance (Ken'ichiro Takahashi) cho lớp {@link State} của giao diện cũ.
 *
 * <p>Ý tưởng: thay vì đếm khoảng cách từng ô như Manhattan, ta chỉ quan tâm
 * "ô đang ở hàng nào cần về hàng đích nào" (bỏ qua vị trí cột). Bảng khoảng cách
 * được dựng trước một lần bằng BFS rồi tra cứu O(1) - phần dựng bảng nằm ở
 * {@link WalkingDistanceTables} (gói heuristics) và được dùng chung với search engine mới.</p>
 */
public final class WalkingDistance {
    private WalkingDistance() {
    }

    /** Lấy (và dựng nếu chưa có) bảng Walking Distance cho kích thước và lớp đích của ô trống. */
    public static WalkingDistanceTables.LongByteTable tableFor(int size, int blankGoalLine) {
        return WalkingDistanceTables.tableFor(size, blankGoalLine);
    }

    /** Giá trị heuristic = thành phần dọc + thành phần ngang. */
    public static int walkingDistance(State state, State goalState) {
        int size = state.getSize();
        int length = state.getLength();

        int[] goalPos = new int[length];
        for (int i = 0; i < length; i++) {
            goalPos[goalState.value[i]] = i;
        }
        int blankGoal = goalPos[0];

        byte[][] vertical = new byte[size][size];
        byte[][] horizontal = new byte[size][size];
        int blank = state.posBlank(state.value);
        for (int i = 0; i < length; i++) {
            int tile = state.value[i];
            if (tile == 0) continue;
            int gi = goalPos[tile];
            vertical[i / size][gi / size]++;
            horizontal[i % size][gi % size]++;
        }

        int vd = tableFor(size, blankGoal / size).get(WalkingDistanceTables.pack(vertical, blank / size, size));
        int hd = tableFor(size, blankGoal % size).get(WalkingDistanceTables.pack(horizontal, blank % size, size));
        return Math.max(0, vd) + Math.max(0, hd);
    }
}
