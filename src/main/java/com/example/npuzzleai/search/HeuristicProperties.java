package com.example.npuzzleai.search;

/**
 * Khai báo (claim) về tính chất của heuristic - không phải bằng chứng.
 * Bộ test kiểm định vét cạn trên 3x3 sẽ xác minh claim này.
 *
 * @param claimedAdmissible     h(s) &lt;= h*(s) với mọi s
 * @param claimedConsistent     h(s) &lt;= 1 + h(s') với mọi cạnh (s, s')
 * @param requiresPreprocessing cần dựng bảng trước (PDB, Walking Distance)
 * @param experimental          heuristic thử nghiệm, chưa được chứng minh
 * @param note                  ghi chú hiển thị
 */
public record HeuristicProperties(boolean claimedAdmissible,
                                  boolean claimedConsistent,
                                  boolean requiresPreprocessing,
                                  boolean experimental,
                                  String note) {

    public static HeuristicProperties unknown() {
        return new HeuristicProperties(false, false, false, true, "Chưa khai báo tính chất");
    }

    public static HeuristicProperties admissibleAndConsistent(String note) {
        return new HeuristicProperties(true, true, false, false, note);
    }

    public static HeuristicProperties experimental(String note) {
        return new HeuristicProperties(false, false, false, true, note);
    }

    public HeuristicProperties withPreprocessing() {
        return new HeuristicProperties(claimedAdmissible, claimedConsistent, true, experimental, note);
    }

    /** Ký hiệu ngắn: A = admissible, C = consistent, P = cần tiền xử lý, ? = thử nghiệm. */
    public String flags() {
        StringBuilder sb = new StringBuilder();
        if (claimedAdmissible) sb.append('A');
        if (claimedConsistent) sb.append('C');
        if (requiresPreprocessing) sb.append('P');
        if (experimental) sb.append('?');
        return sb.length() == 0 ? "-" : sb.toString();
    }
}
