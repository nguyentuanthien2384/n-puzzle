package com.example.npuzzleai.heuristics.pdb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Một pattern: tập ô số được theo dõi chính xác trong không gian trừu tượng.
 *
 * @param size  kích thước bảng
 * @param tiles các ô số (1..n-1, phân biệt) thuộc pattern
 */
public record PatternDefinition(int size, int[] tiles) {
    public PatternDefinition {
        int n = size * size;
        tiles = tiles.clone();
        if (tiles.length == 0 || tiles.length >= n) {
            throw new IllegalArgumentException("Pattern phải có từ 1 đến " + (n - 2) + " ô");
        }
        boolean[] seen = new boolean[n];
        for (int t : tiles) {
            if (t <= 0 || t >= n || seen[t]) {
                throw new IllegalArgumentException("Ô pattern không hợp lệ hoặc bị lặp: " + t);
            }
            seen[t] = true;
        }
    }

    @Override
    public int[] tiles() {
        return tiles.clone();
    }

    public int tileCount() {
        return tiles.length;
    }

    int tileAt(int i) {
        return tiles[i];
    }

    public String key() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tiles.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(tiles[i]);
        }
        return sb.toString();
    }

    /** Đọc "1,2,3;4,5,6;7,8" thành danh sách pattern. */
    public static List<PatternDefinition> parsePartition(String spec, int size) {
        List<PatternDefinition> list = new ArrayList<>();
        for (String group : spec.split(";")) {
            String g = group.trim();
            if (g.isEmpty()) continue;
            String[] parts = g.split("[,\\s]+");
            int[] tiles = new int[parts.length];
            for (int i = 0; i < parts.length; i++) tiles[i] = Integer.parseInt(parts[i].trim());
            list.add(new PatternDefinition(size, tiles));
        }
        if (list.isEmpty()) throw new IllegalArgumentException("Partition rỗng");
        return list;
    }

    /** Kiểm tra các pattern đôi một rời nhau - điều kiện để cộng PDB mà vẫn chấp nhận được. */
    public static void requireDisjoint(List<PatternDefinition> patterns) {
        int size = patterns.get(0).size();
        boolean[] used = new boolean[size * size];
        for (PatternDefinition p : patterns) {
            if (p.size() != size) throw new IllegalArgumentException("Các pattern phải cùng kích thước bảng");
            for (int t : p.tiles) {
                if (used[t]) {
                    throw new IllegalArgumentException("Ô " + t + " thuộc nhiều pattern - cộng PDB sẽ đếm trùng "
                            + "chi phí và mất tính chấp nhận được");
                }
                used[t] = true;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PatternDefinition p && p.size == size && Arrays.equals(p.tiles, tiles);
    }

    @Override
    public int hashCode() {
        return 31 * size + Arrays.hashCode(tiles);
    }

    @Override
    public String toString() {
        return "{" + key() + "}";
    }
}
