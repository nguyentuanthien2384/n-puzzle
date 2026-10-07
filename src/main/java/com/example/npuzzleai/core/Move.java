package com.example.npuzzleai.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Nước đi trên bảng N-Puzzle, mô tả theo hướng di chuyển của <b>ô trống</b>.
 *
 * <p>Ví dụ {@link #UP}: ô trống đi lên một hàng, tức ô số phía trên trượt xuống.
 * Quy ước này khớp với các phương thức UP/DOWN/LEFT/RIGHT của lớp State cũ.</p>
 */
public enum Move {
    UP(-1, 0, 'U'),
    DOWN(1, 0, 'D'),
    LEFT(0, -1, 'L'),
    RIGHT(0, 1, 'R');

    /** Số hướng đi - dùng cho vòng lặp nhanh trong thuật toán thay vì values() (cấp phát mảng mới). */
    public static final int COUNT = 4;
    private static final Move[] VALUES = values();

    private final int dr;
    private final int dc;
    private final char symbol;

    Move(int dr, int dc, char symbol) {
        this.dr = dr;
        this.dc = dc;
        this.symbol = symbol;
    }

    public static Move byOrdinal(int ordinal) {
        return VALUES[ordinal];
    }

    public int dr() {
        return dr;
    }

    public int dc() {
        return dc;
    }

    public char symbol() {
        return symbol;
    }

    public Move opposite() {
        return switch (this) {
            case UP -> DOWN;
            case DOWN -> UP;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }

    public static Move fromSymbol(char c) {
        for (Move m : VALUES) {
            if (m.symbol == Character.toUpperCase(c)) return m;
        }
        throw new IllegalArgumentException("Ký hiệu nước đi không hợp lệ: " + c);
    }

    /** Nước đi biến {@code from} thành {@code to}; ném lỗi nếu hai bảng không kề nhau. */
    public static Move between(Board from, Board to) {
        int size = from.size();
        int a = from.blankIndex();
        int b = to.blankIndex();
        for (Move m : VALUES) {
            if (from.canMove(m) && a + m.dr * size + m.dc == b && from.move(m).equals(to)) return m;
        }
        throw new IllegalArgumentException("Hai trạng thái không cách nhau đúng một nước đi");
    }

    /** Chuỗi ký hiệu gọn, ví dụ "RRDLU". */
    public static String format(List<Move> moves) {
        StringBuilder sb = new StringBuilder(moves.size());
        for (Move m : moves) sb.append(m.symbol);
        return sb.toString();
    }

    public static List<Move> parseSequence(String text) {
        List<Move> moves = new ArrayList<>(text.length());
        for (char c : text.toCharArray()) {
            if (!Character.isWhitespace(c)) moves.add(fromSymbol(c));
        }
        return moves;
    }
}
