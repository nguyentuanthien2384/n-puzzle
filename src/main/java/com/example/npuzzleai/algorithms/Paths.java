package com.example.npuzzleai.algorithms;

import com.example.npuzzleai.core.Move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Truy vết chuỗi nước đi từ node đích về gốc. */
final class Paths {
    private Paths() {
    }

    /** Node có con trỏ cha và nước đi dẫn tới nó (null ở gốc). */
    interface Step {
        Step parent();

        Move move();
    }

    static List<Move> movesTo(Step node) {
        List<Move> moves = new ArrayList<>();
        for (Step s = node; s != null && s.move() != null; s = s.parent()) moves.add(s.move());
        Collections.reverse(moves);
        return moves;
    }

    /** Chuỗi nước đi từ ngăn xếp của DFS (IDA*, RBFS). */
    static List<Move> fromStack(Move[] stack, int length) {
        List<Move> moves = new ArrayList<>(length);
        for (int i = 0; i < length; i++) moves.add(stack[i]);
        return moves;
    }
}
