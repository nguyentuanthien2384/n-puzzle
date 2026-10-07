package com.example.npuzzleai.search;

import com.example.npuzzleai.core.Board;

/**
 * Một sự kiện trong quá trình tìm kiếm, dùng cho Search Replay và heatmap.
 *
 * @param type           loại sự kiện
 * @param board          trạng thái liên quan
 * @param g              chi phí đã đi
 * @param h              giá trị heuristic
 * @param f              giá trị ưu tiên (A*: g + h; Weighted A*: g + w*h; RBFS/SMA*: f đã backup)
 * @param depth          độ sâu trong cây tìm kiếm
 * @param expansionIndex số node đã mở rộng tại thời điểm phát sự kiện
 */
public record SearchEvent(Type type, Board board, int g, int h, double f, int depth, long expansionIndex) {
    public enum Type {
        EXPAND, GENERATE, PRUNE, GOAL
    }
}
