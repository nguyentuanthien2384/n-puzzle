package com.example.npuzzleai.core;

import java.util.Objects;

/** Bài toán tìm đường: trạng thái xuất phát + trạng thái đích cùng kích thước. */
public record PuzzleProblem(Board start, Goal goal) {
    public PuzzleProblem {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(goal, "goal");
        if (start.size() != goal.size()) {
            throw new IllegalArgumentException("Kích thước trạng thái bắt đầu (" + start.size()
                    + ") và đích (" + goal.size() + ") không khớp");
        }
    }

    public int size() {
        return start.size();
    }
}
