package com.example.npuzzleai.heuristics.pdb;

import com.example.npuzzleai.core.Goal;

import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Metadata tự mô tả của một Pattern Database - không thể vô tình dùng PDB của đích khác.
 *
 * @param size           kích thước bảng
 * @param goalTiles      trạng thái đích dùng để dựng
 * @param patternTiles   các ô thuộc pattern
 * @param costModel      mô hình chi phí ("pattern-moves": chỉ tính nước đi của ô pattern - cộng được)
 * @param builderVersion phiên bản thuật toán dựng
 * @param entries        số entry của bảng
 * @param checksum       CRC32 của bảng
 * @param buildMillis    thời gian dựng (ms)
 */
public record PdbMetadata(int size, int[] goalTiles, int[] patternTiles, String costModel,
                          String builderVersion, int entries, long checksum, long buildMillis) {
    public static final String COST_MODEL = "pattern-moves";

    public PdbMetadata {
        goalTiles = goalTiles.clone();
        patternTiles = patternTiles.clone();
    }

    @Override
    public int[] goalTiles() {
        return goalTiles.clone();
    }

    @Override
    public int[] patternTiles() {
        return patternTiles.clone();
    }

    public long goalHash() {
        CRC32 crc = new CRC32();
        for (int t : goalTiles) crc.update(t);
        return crc.getValue();
    }

    public boolean matches(PatternDefinition pattern, Goal goal) {
        return size == goal.size()
                && Arrays.equals(goalTiles, goal.board().toArray())
                && Arrays.equals(patternTiles, pattern.tiles())
                && COST_MODEL.equals(costModel);
    }

    @Override
    public String toString() {
        return "PDB " + size + "x" + size + " pattern=" + Arrays.toString(patternTiles)
                + " entries=" + entries + " crc32=" + Long.toHexString(checksum)
                + " goal=" + Long.toHexString(goalHash()) + " builder=" + builderVersion;
    }
}
