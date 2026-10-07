package com.example.npuzzleai.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Trạng thái bảng N-Puzzle bất biến (immutable), độc lập JavaFX.
 *
 * <p>Ô được đánh chỉ số theo hàng (row-major), giá trị 0 là ô trống. Mỗi nước đi
 * tạo một {@code Board} mới nên có thể dùng an toàn làm khoá HashMap, lưu trong
 * trace và chia sẻ giữa các luồng benchmark.</p>
 *
 * <p>Với bảng tới 5x5, {@link #keyLow()}/{@link #keyHigh()} cho khoá nén chính xác
 * (4 bit/ô cho &lt;= 4x4, 5 bit/ô cho 5x5 - ô cuối suy ra được vì là hoán vị),
 * dùng cho bảng chuyển vị (transposition table) của IDA*-TT.</p>
 */
public final class Board {
    public static final int MIN_SIZE = 2;
    public static final int MAX_SIZE = 10;

    private final int size;
    private final byte[] tiles;
    private final int blank;
    private final int hash;

    private Board(int size, byte[] tiles, int blank) {
        this.size = size;
        this.tiles = tiles;
        this.blank = blank;
        this.hash = Arrays.hashCode(tiles);
    }

    /** Tạo bảng từ danh sách giá trị; kiểm tra đúng là hoán vị 0..n-1. */
    public static Board of(int size, int... values) {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw new IllegalArgumentException("Kích thước bảng phải trong khoảng " + MIN_SIZE + ".." + MAX_SIZE);
        }
        int n = size * size;
        if (values.length != n) {
            throw new IllegalArgumentException("Bảng " + size + "x" + size + " cần đúng " + n + " giá trị, nhận " + values.length);
        }
        byte[] t = new byte[n];
        boolean[] seen = new boolean[n];
        int blank = -1;
        for (int i = 0; i < n; i++) {
            int v = values[i];
            if (v < 0 || v >= n || seen[v]) {
                throw new IllegalArgumentException("Trạng thái phải chứa đúng các giá trị từ 0 đến " + (n - 1));
            }
            seen[v] = true;
            t[i] = (byte) v;
            if (v == 0) blank = i;
        }
        return new Board(size, t, blank);
    }

    /** Suy ra kích thước từ độ dài mảng (phải là số chính phương). */
    public static Board fromArray(int[] values) {
        int size = (int) Math.round(Math.sqrt(values.length));
        if (size * size != values.length) {
            throw new IllegalArgumentException("Số ô (" + values.length + ") không phải số chính phương");
        }
        return of(size, values);
    }

    /** Đọc bảng từ chuỗi, phân tách bởi khoảng trắng, dấu phẩy hoặc chấm phẩy. */
    public static Board parse(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Chuỗi trạng thái rỗng");
        String[] parts = trimmed.split("[\\s,;]+");
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                values[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Giá trị không hợp lệ: '" + parts[i] + "'");
            }
        }
        return fromArray(values);
    }

    public int size() {
        return size;
    }

    public int cellCount() {
        return tiles.length;
    }

    public int tileAt(int index) {
        return tiles[index];
    }

    public int tileAt(int row, int col) {
        return tiles[row * size + col];
    }

    public int blankIndex() {
        return blank;
    }

    public int blankRow() {
        return blank / size;
    }

    public int blankCol() {
        return blank % size;
    }

    public int[] toArray() {
        int[] out = new int[tiles.length];
        for (int i = 0; i < tiles.length; i++) out[i] = tiles[i];
        return out;
    }

    /** Mảng vị trí: positions()[tile] = chỉ số ô đang chứa tile. */
    public int[] positions() {
        int[] pos = new int[tiles.length];
        for (int i = 0; i < tiles.length; i++) pos[tiles[i]] = i;
        return pos;
    }

    public boolean canMove(Move m) {
        return switch (m) {
            case UP -> blank >= size;
            case DOWN -> blank < tiles.length - size;
            case LEFT -> blank % size != 0;
            case RIGHT -> blank % size != size - 1;
        };
    }

    /** Ô mà ô trống sẽ chuyển tới (giả định nước đi hợp lệ). */
    public int targetIndex(Move m) {
        return blank + m.dr() * size + m.dc();
    }

    /** Giá trị ô số sẽ trượt khi thực hiện nước đi (giả định hợp lệ). */
    public int movedTile(Move m) {
        return tiles[targetIndex(m)];
    }

    public Board move(Move m) {
        if (!canMove(m)) {
            throw new IllegalStateException("Nước đi " + m + " không hợp lệ tại ô trống " + blank);
        }
        int target = targetIndex(m);
        byte[] next = tiles.clone();
        next[blank] = next[target];
        next[target] = 0;
        return new Board(size, next, target);
    }

    public List<Move> legalMoves() {
        List<Move> moves = new ArrayList<>(4);
        for (int d = 0; d < Move.COUNT; d++) {
            Move m = Move.byOrdinal(d);
            if (canMove(m)) moves.add(m);
        }
        return moves;
    }

    /** Áp dụng chuỗi nước đi; ném lỗi nếu có nước đi không hợp lệ. */
    public Board apply(List<Move> moves) {
        Board b = this;
        for (Move m : moves) b = b.move(m);
        return b;
    }

    /** Có khoá nén chính xác cho IDA*-TT hay không (bảng tới 5x5). */
    public boolean hasCompactKey() {
        return size <= 5;
    }

    /** Phần thấp của khoá nén: &lt;= 4x4 là toàn bộ bảng (4 bit/ô); 5x5 là ô 0..11 (5 bit/ô). */
    public long keyLow() {
        long key = 0;
        if (size <= 4) {
            for (int i = 0; i < tiles.length; i++) key |= ((long) tiles[i]) << (4 * i);
        } else if (size == 5) {
            for (int i = 0; i < 12; i++) key |= ((long) tiles[i]) << (5 * i);
        } else {
            throw new UnsupportedOperationException("Khoá nén chỉ hỗ trợ bảng tới 5x5");
        }
        return key;
    }

    /** Phần cao của khoá nén: 0 với &lt;= 4x4; 5x5 là ô 12..23 (ô 24 suy ra từ hoán vị). */
    public long keyHigh() {
        if (size <= 4) return 0L;
        if (size != 5) throw new UnsupportedOperationException("Khoá nén chỉ hỗ trợ bảng tới 5x5");
        long key = 0;
        for (int i = 12; i < 24; i++) key |= ((long) tiles[i]) << (5 * (i - 12));
        return key;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Board other)) return false;
        return hash == other.hash && size == other.size && Arrays.equals(tiles, other.tiles);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    /** Dạng một dòng, ví dụ "1 2 3 4 5 6 7 8 0" - cũng là định dạng đọc lại được bằng {@link #parse}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(tiles.length * 3);
        for (int i = 0; i < tiles.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(tiles[i]);
        }
        return sb.toString();
    }

    /** Dạng lưới nhiều dòng để in ra console. */
    public String toGrid() {
        int width = String.valueOf(tiles.length - 1).length();
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int v = tiles[r * size + c];
                String cell = v == 0 ? "." : String.valueOf(v);
                sb.append(" ".repeat(width - cell.length() + 1)).append(cell);
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
