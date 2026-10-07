package com.example.npuzzleai.search;

/** Kết quả cuối của một lần tìm kiếm. */
public enum SearchStatus {
    SOLVED("Đã giải"),
    UNSOLVABLE("Vô nghiệm"),
    NO_SOLUTION("Không tìm thấy"),
    TIMEOUT("Quá thời gian"),
    NODE_LIMIT("Vượt giới hạn node"),
    MEMORY_LIMIT("Vượt ngân sách bộ nhớ"),
    CANCELLED("Đã dừng"),
    UNSUPPORTED("Không hỗ trợ"),
    FAILED("Lỗi");

    private final String label;

    SearchStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Thất bại do giới hạn tài nguyên (khác với vô nghiệm hay lỗi cấu hình). */
    public boolean isResourceLimit() {
        return this == TIMEOUT || this == NODE_LIMIT || this == MEMORY_LIMIT;
    }
}
