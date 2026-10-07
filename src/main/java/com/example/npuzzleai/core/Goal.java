package com.example.npuzzleai.core;

/**
 * Trạng thái đích tổng quát.
 *
 * <p>Mọi heuristic, Pattern Database và kiểm tra khả giải nhận {@code Goal} thay vì
 * giả định cứng "1 2 3 / 4 5 6 / 7 8 0". Vị trí đích của từng ô được tính sẵn
 * (rowOf/colOf) để vòng lặp heuristic không phải tìm kiếm.</p>
 */
public final class Goal {
    public static final String STANDARD = "standard";
    public static final String BLANK_FIRST = "blank-first";
    public static final String CUSTOM = "custom";

    private final Board board;
    private final int[] posOf;
    private final int[] rowOf;
    private final int[] colOf;
    private final String name;

    private Goal(Board board) {
        this.board = board;
        int n = board.cellCount();
        int size = board.size();
        this.posOf = board.positions();
        this.rowOf = new int[n];
        this.colOf = new int[n];
        for (int t = 0; t < n; t++) {
            rowOf[t] = posOf[t] / size;
            colOf[t] = posOf[t] % size;
        }
        this.name = detectName(board);
    }

    public static Goal of(Board board) {
        return new Goal(board);
    }

    /** Đích chuẩn: 1, 2, ..., n-1 rồi ô trống ở góc dưới phải (đích kiểu 2 của giao diện). */
    public static Goal standard(int size) {
        int n = size * size;
        int[] v = new int[n];
        for (int i = 0; i < n - 1; i++) v[i] = i + 1;
        v[n - 1] = 0;
        return new Goal(Board.of(size, v));
    }

    /** Đích ô trống trước: 0, 1, ..., n-1 (đích kiểu 1 của giao diện). */
    public static Goal blankFirst(int size) {
        int n = size * size;
        int[] v = new int[n];
        for (int i = 0; i < n; i++) v[i] = i;
        return new Goal(Board.of(size, v));
    }

    /** Ánh xạ từ biến State.goal cũ: 1 = ô trống trước, 2 = chuẩn. */
    public static Goal fromLegacy(int goalType, int size) {
        return goalType == 1 ? blankFirst(size) : standard(size);
    }

    /** Đọc "standard", "blank-first" hoặc danh sách ô đầy đủ. */
    public static Goal parse(String spec, int size) {
        String s = spec.trim();
        if (s.equalsIgnoreCase(STANDARD)) return standard(size);
        if (s.equalsIgnoreCase(BLANK_FIRST)) return blankFirst(size);
        Board b = Board.parse(s);
        if (b.size() != size) {
            throw new IllegalArgumentException("Đích có kích thước " + b.size() + "x" + b.size() + ", cần " + size + "x" + size);
        }
        return new Goal(b);
    }

    private static String detectName(Board b) {
        int n = b.cellCount();
        boolean standard = b.tileAt(n - 1) == 0;
        boolean blankFirst = true;
        for (int i = 0; i < n; i++) {
            if (standard && i < n - 1 && b.tileAt(i) != i + 1) standard = false;
            if (b.tileAt(i) != i) blankFirst = false;
        }
        if (standard) return STANDARD;
        if (blankFirst) return BLANK_FIRST;
        return CUSTOM;
    }

    public Board board() {
        return board;
    }

    public int size() {
        return board.size();
    }

    public int positionOf(int tile) {
        return posOf[tile];
    }

    public int rowOf(int tile) {
        return rowOf[tile];
    }

    public int colOf(int tile) {
        return colOf[tile];
    }

    public int blankPosition() {
        return posOf[0];
    }

    public boolean isGoal(Board b) {
        return board.equals(b);
    }

    /** "standard", "blank-first" hoặc "custom". */
    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        return obj instanceof Goal other && board.equals(other.board);
    }

    @Override
    public int hashCode() {
        return board.hashCode();
    }

    @Override
    public String toString() {
        return name.equals(CUSTOM) ? board.toString() : name;
    }
}
