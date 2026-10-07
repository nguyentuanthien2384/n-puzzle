package com.example.npuzzleai.search;

/**
 * Đặc tính khai báo của một thuật toán - dùng để lọc tổ hợp trong benchmark và
 * để quyết định có được kiểm tra tính tối ưu hay không.
 *
 * @param usesHeuristic                thuật toán có dùng hàm h hay không
 * @param optimalWithAdmissibleHeuristic đảm bảo lời giải tối ưu khi h chấp nhận được
 * @param linearMemory                 bộ nhớ tuyến tính theo độ sâu (IDA*, RBFS)
 * @param maxRecommendedSize           kích thước bảng lớn nhất nên cho phép
 * @param family                       nhóm thuật toán để hiển thị
 */
public record AlgorithmProperties(boolean usesHeuristic,
                                  boolean optimalWithAdmissibleHeuristic,
                                  boolean linearMemory,
                                  int maxRecommendedSize,
                                  String family) {
}
