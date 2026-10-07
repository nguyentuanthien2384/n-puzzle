package com.example.npuzzleai.search;

/** Mức ghi sự kiện tìm kiếm. */
public enum TraceMode {
    /** Không phát sự kiện - dùng cho benchmark. */
    OFF,
    /** Phát 1/K sự kiện expand - dùng cho puzzle vừa. */
    SAMPLED,
    /** Phát mọi sự kiện expand/generate/prune - chỉ dùng cho puzzle nhỏ. */
    FULL_TRACE
}
