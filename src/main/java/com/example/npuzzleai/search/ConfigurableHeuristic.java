package com.example.npuzzleai.search;

/**
 * Heuristic (thường là plugin) nhận tham số qua mã "ten:tham_so", ví dụ {@code learned:models/m.model}.
 * Registry dùng phần trước dấu ':' của {@link #id()} làm mã gốc và gọi {@link #configure} cho tham số.
 */
public interface ConfigurableHeuristic extends Heuristic {
    Heuristic configure(String parameter);
}
