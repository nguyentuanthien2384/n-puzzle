package com.example.npuzzleai.verify;

/**
 * Kết quả kiểm định nội tại (intrinsic) của một heuristic trên toàn bộ không gian trạng thái.
 *
 * @param heuristicId              mã heuristic
 * @param displayName              tên hiển thị
 * @param goalName                 đích dùng để kiểm định
 * @param states                   số trạng thái đã kiểm tra (toàn bộ thành phần tới được)
 * @param edges                    số cạnh (s, s') đã kiểm tra tính nhất quán
 * @param admissibilityViolations  số trạng thái có h &gt; h*
 * @param maxOverestimate          max(h - h*) (0 nếu không vi phạm)
 * @param counterexample           một trạng thái vi phạm (rỗng nếu không có)
 * @param counterexampleH          h tại phản ví dụ
 * @param counterexampleHStar      h* tại phản ví dụ
 * @param consistencyViolations    số cạnh có h(s) &gt; 1 + h(s')
 * @param goalValue                h tại đích (phải bằng 0)
 * @param meanH                    trung bình h
 * @param meanHStar                trung bình h*
 * @param meanError                trung bình (h* - h)
 * @param medianError              trung vị (h* - h)
 * @param exactRate                tỉ lệ trạng thái có h = h*
 * @param meanHByDistance          trung bình h theo từng h* (chỉ số = h*)
 * @param claimedAdmissible        khai báo admissible
 * @param claimedConsistent        khai báo consistent
 * @param experimental             heuristic thử nghiệm
 * @param elapsedMillis            thời gian kiểm định
 */
public record HeuristicReport(String heuristicId,
                              String displayName,
                              String goalName,
                              int states,
                              long edges,
                              long admissibilityViolations,
                              int maxOverestimate,
                              String counterexample,
                              int counterexampleH,
                              int counterexampleHStar,
                              long consistencyViolations,
                              int goalValue,
                              double meanH,
                              double meanHStar,
                              double meanError,
                              double medianError,
                              double exactRate,
                              double[] meanHByDistance,
                              boolean claimedAdmissible,
                              boolean claimedConsistent,
                              boolean experimental,
                              long elapsedMillis) {

    public boolean admissible() {
        return admissibilityViolations == 0 && goalValue == 0;
    }

    public boolean consistent() {
        return consistencyViolations == 0;
    }

    /** Mọi khai báo của heuristic đều đúng trên dữ liệu chính xác. */
    public boolean claimsHold() {
        return (!claimedAdmissible || admissible()) && (!claimedConsistent || consistent());
    }

    /** Kết luận ngắn cho bảng báo cáo. */
    public String verdict() {
        if (!claimsHold()) return "SAI KHAI BÁO";
        if (admissible() && consistent()) return "Admissible + Consistent";
        if (admissible()) return "Admissible, không nhất quán";
        return "KHÔNG admissible";
    }
}
