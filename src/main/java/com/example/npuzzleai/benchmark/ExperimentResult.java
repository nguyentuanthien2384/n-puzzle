package com.example.npuzzleai.benchmark;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Toàn bộ kết quả của một thí nghiệm, đủ để xuất thư mục tái lập.
 *
 * @param config        cấu hình
 * @param dataset       dataset đã dùng
 * @param records       mọi lần chạy (sắp theo tổ hợp, instance, lần lặp)
 * @param preprocessNs  thời gian tiền xử lý theo heuristic
 * @param environment   thông tin môi trường
 * @param startedAt     thời điểm bắt đầu
 * @param finishedAt    thời điểm kết thúc
 * @param cancelled     bị dừng giữa chừng
 * @param log           nhật ký
 */
public record ExperimentResult(ExperimentConfig config,
                               Dataset dataset,
                               List<RunRecord> records,
                               Map<String, Long> preprocessNs,
                               Map<String, Object> environment,
                               Instant startedAt,
                               Instant finishedAt,
                               boolean cancelled,
                               List<String> log) {

    public List<SummaryRow> summary() {
        return SummaryRow.summarize(records, preprocessNs);
    }

    /** Số lần chạy có cam kết tối ưu nhưng cho độ dài sai, hoặc đường đi không hợp lệ. */
    public long correctnessFailures() {
        return records.stream()
                .filter(r -> (r.solved() && !r.pathValid()) || Boolean.FALSE.equals(r.optimalityOk()))
                .count();
    }
}
