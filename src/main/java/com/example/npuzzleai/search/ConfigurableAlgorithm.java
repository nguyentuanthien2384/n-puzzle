package com.example.npuzzleai.search;

/**
 * Thuật toán (thường là plugin) nhận tham số qua mã "ten:tham_so", ví dụ {@code focal:2.0}.
 * Registry dùng phần trước dấu ':' của {@link #id()} làm mã gốc và gọi {@link #configure} cho tham số.
 */
public interface ConfigurableAlgorithm extends SearchAlgorithm {
    SearchAlgorithm configure(String parameter);
}
