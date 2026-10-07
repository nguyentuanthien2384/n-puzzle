package com.example.npuzzleai.benchmark;

import com.example.npuzzleai.search.SearchMetrics;
import com.example.npuzzleai.search.SearchStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Một lần chạy (thuật toán × heuristic × instance × lần lặp) - một dòng trong raw-results.csv.
 *
 * @param runOrder      thứ tự thực thi thực tế (sau khi xáo)
 * @param algorithm     mã thuật toán
 * @param heuristic     mã heuristic ("none" nếu không dùng)
 * @param instance      chỉ số instance trong dataset
 * @param repetition    lần lặp (bắt đầu từ 1)
 * @param status        trạng thái kết thúc
 * @param optimalLength độ dài tối ưu tham chiếu (-1 nếu chưa biết): chính xác (3x3) hoặc đồng thuận của
 *                      các tổ hợp có cam kết tối ưu (4x4 trở lên)
 * @param pathValid     chuỗi nước đi được kiểm chứng độc lập là tới đích
 * @param optimalityOk  null nếu không kiểm tra được; true/false nếu đã đối chiếu với độ dài tối ưu
 * @param metrics       toàn bộ bộ đếm
 * @param thread        tên luồng worker
 * @param claimsOptimal tổ hợp có cam kết tối ưu (thuật toán tối ưu + heuristic khai báo admissible)
 */
public record RunRecord(int runOrder,
                        String algorithm,
                        String heuristic,
                        int instance,
                        int repetition,
                        SearchStatus status,
                        int optimalLength,
                        boolean pathValid,
                        Boolean optimalityOk,
                        SearchMetrics metrics,
                        String thread,
                        boolean claimsOptimal) {

    /** Bản sao với độ dài tham chiếu và kết quả đối chiếu mới (dùng cho kiểm tra đồng thuận). */
    public RunRecord withReference(int reference, Boolean ok) {
        return new RunRecord(runOrder, algorithm, heuristic, instance, repetition, status, reference, pathValid, ok,
                metrics, thread, claimsOptimal);
    }

    public boolean solved() {
        return status == SearchStatus.SOLVED;
    }

    public long solutionLength() {
        return metrics.solutionLength;
    }

    /** solutionLength / optimalLength; NaN nếu chưa biết tối ưu hoặc chưa giải. */
    public double optimalityGap() {
        if (!solved() || optimalLength < 0) return Double.NaN;
        if (optimalLength == 0) return metrics.solutionLength == 0 ? 1.0 : Double.NaN;
        return (double) metrics.solutionLength / optimalLength;
    }

    public String combo() {
        return heuristic.equals("none") ? algorithm : algorithm + " + " + heuristic;
    }

    public static List<String> csvHeader() {
        List<String> header = new ArrayList<>(List.of("runOrder", "algorithm", "heuristic", "instance",
                "repetition", "status", "optimalLength", "optimalityGap", "pathValid", "claimsOptimal", "optimalityOk",
                "thread"));
        header.addAll(new SearchMetrics().toMap().keySet());
        return header;
    }

    public List<String> csvRow() {
        List<String> row = new ArrayList<>();
        row.add(String.valueOf(runOrder));
        row.add(algorithm);
        row.add(heuristic);
        row.add(String.valueOf(instance));
        row.add(String.valueOf(repetition));
        row.add(status.name());
        row.add(String.valueOf(optimalLength));
        double gap = optimalityGap();
        row.add(Double.isNaN(gap) ? "" : String.format(Locale.ROOT, "%.4f", gap));
        row.add(String.valueOf(pathValid));
        row.add(String.valueOf(claimsOptimal));
        row.add(optimalityOk == null ? "" : String.valueOf(optimalityOk));
        row.add(thread);
        for (Map.Entry<String, Long> e : metrics.toMap().entrySet()) row.add(String.valueOf(e.getValue()));
        return row;
    }
}
