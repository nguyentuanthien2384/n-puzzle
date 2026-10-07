package com.example.npuzzleai.benchmark;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;

import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Bộ instance cố định cho một thí nghiệm.
 *
 * @param name          tên (ví dụ "hard-15p")
 * @param description   mô tả cách sinh
 * @param goal          trạng thái đích chung
 * @param seed          seed dùng để sinh (0 nếu nạp từ file)
 * @param instances     các trạng thái xuất phát
 * @param optimalLength độ dài tối ưu đã biết của từng instance (-1 nếu chưa biết), có thể null
 */
public record Dataset(String name, String description, Goal goal, long seed,
                      List<Board> instances, int[] optimalLength) {

    public Dataset {
        instances = List.copyOf(instances);
        if (optimalLength != null) {
            if (optimalLength.length != instances.size()) {
                throw new IllegalArgumentException("Số độ dài tối ưu không khớp số instance");
            }
            optimalLength = optimalLength.clone();
        }
        for (Board b : instances) {
            if (b.size() != goal.size()) throw new IllegalArgumentException("Instance khác kích thước đích");
        }
    }

    public int size() {
        return goal.size();
    }

    public int optimal(int index) {
        return optimalLength == null ? -1 : optimalLength[index];
    }

    @Override
    public int[] optimalLength() {
        return optimalLength == null ? null : optimalLength.clone();
    }

    public Dataset withOptimalLengths(int[] lengths) {
        return new Dataset(name, description, goal, seed, instances, lengths);
    }

    /** CRC32 của đích + mọi instance - ghi vào manifest để xác nhận đúng dataset khi tái tạo. */
    public long checksum() {
        CRC32 crc = new CRC32();
        for (int t : goal.board().toArray()) crc.update(t);
        for (Board b : instances) for (int t : b.toArray()) crc.update(t);
        return crc.getValue();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Dataset d && d.name.equals(name) && d.goal.equals(goal)
                && d.instances.equals(instances) && Arrays.equals(d.optimalLength, optimalLength);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + instances.hashCode();
    }
}
