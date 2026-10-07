package com.example.npuzzleai.benchmark;

import java.nio.file.Path;
import java.util.List;

/**
 * Cấu hình đầy đủ của một thí nghiệm - mọi giá trị đều được ghi vào manifest.json.
 *
 * @param name             tên thí nghiệm (dùng trong tên thư mục)
 * @param dataset          tên dataset dựng sẵn hoặc "file:đường_dẫn"
 * @param algorithms       mã thuật toán (ví dụ "astar", "ida", "wastar:2")
 * @param heuristics       mã heuristic; thuật toán không dùng heuristic chỉ chạy một lần với "none"
 * @param repetitions      số lần lặp mỗi tổ hợp × instance
 * @param warmupRuns       số lần chạy khởi động (không ghi) cho mỗi tổ hợp - giảm ảnh hưởng JIT
 * @param timeoutMillis    giới hạn thời gian mỗi lần chạy
 * @param maxExpanded      giới hạn node mở rộng (0 = không)
 * @param maxMemoryBytes   ngân sách bộ nhớ ước lượng (0 = không)
 * @param threads          số luồng worker (executor riêng, không dùng common pool)
 * @param seed             seed sinh dataset và xáo thứ tự chạy
 * @param shuffleOrder     xáo thứ tự các lần chạy để giảm thiên lệch theo thứ tự
 * @param outputRoot       thư mục gốc chứa thư mục kết quả
 */
public record ExperimentConfig(String name,
                               String dataset,
                               List<String> algorithms,
                               List<String> heuristics,
                               int repetitions,
                               int warmupRuns,
                               long timeoutMillis,
                               long maxExpanded,
                               long maxMemoryBytes,
                               int threads,
                               long seed,
                               boolean shuffleOrder,
                               Path outputRoot) {

    public ExperimentConfig {
        algorithms = List.copyOf(algorithms);
        heuristics = List.copyOf(heuristics);
        if (algorithms.isEmpty()) throw new IllegalArgumentException("Cần ít nhất một thuật toán");
        if (repetitions < 1) throw new IllegalArgumentException("repetitions phải >= 1");
        if (warmupRuns < 0) throw new IllegalArgumentException("warmupRuns không được âm");
        if (threads < 1) throw new IllegalArgumentException("threads phải >= 1");
        if (name == null || name.isBlank()) name = "experiment";
    }

    public static ExperimentConfig quick(String dataset, List<String> algorithms, List<String> heuristics) {
        return new ExperimentConfig("quick", dataset, algorithms, heuristics, 1, 0, 30_000, 0, 0, 1,
                Datasets.DEFAULT_SEED, true, Path.of("experiments"));
    }
}
