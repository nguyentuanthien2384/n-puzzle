package com.example.npuzzleai.heuristics.pdb;

import java.util.zip.CRC32;

/**
 * Bảng PDB chỉ đọc: entry tại chỉ số của bộ vị trí các ô pattern = số nước đi tối thiểu
 * <i>của riêng các ô pattern</i> để đưa chúng về đích (tối thiểu theo mọi vị trí ô trống).
 * Vì chỉ đếm nước đi của ô thuộc pattern, giá trị của các pattern rời nhau cộng được.
 */
public final class PatternDatabase {
    static final byte UNREACHED = 0x7F;

    private final PdbMetadata metadata;
    private final byte[] table;
    private final int[] tiles;
    private final int cells;

    PatternDatabase(PdbMetadata metadata, byte[] table) {
        this.metadata = metadata;
        this.table = table;
        this.tiles = metadata.patternTiles();
        this.cells = metadata.size() * metadata.size();
    }

    public PdbMetadata metadata() {
        return metadata;
    }

    public int entries() {
        return table.length;
    }

    public long bytes() {
        return table.length;
    }

    byte[] table() {
        return table;
    }

    /**
     * Tra cứu theo mảng vị trí của bảng hiện tại (positions[tile] = ô chứa tile).
     * Tính chỉ số trực tiếp, không cấp phát.
     */
    public int lookup(int[] positions) {
        long used = 0;
        int r = 0;
        for (int i = 0; i < tiles.length; i++) {
            int p = positions[tiles[i]];
            int smaller = p - Long.bitCount(used & ((1L << p) - 1));
            r = r * (cells - i) + smaller;
            used |= 1L << p;
        }
        int v = table[r];
        return v == UNREACHED ? 0 : v;
    }

    static long checksum(byte[] table) {
        CRC32 crc = new CRC32();
        crc.update(table, 0, table.length);
        return crc.getValue();
    }
}
