package com.example.npuzzleai.ui;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.heuristics.BasicHeuristics;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Vẽ bảng N-Puzzle trên Canvas với ba chế độ:
 * <ul>
 *   <li>{@link Overlay#NONE}: bảng số thường.</li>
 *   <li>{@link Overlay#TEACHING}: tô màu mỗi ô theo khoảng cách Manhattan tới vị trí đích (ghi số ở góc)
 *       và viền đỏ các ô tham gia xung đột tuyến tính - giải thích vì sao h có giá trị hiện tại.</li>
 *   <li>{@link Overlay#HEATMAP}: số lần ô trống xuất hiện tại mỗi ô trong các node đã mở rộng -
 *       cho thấy "heuristic kéo search về đâu".</li>
 * </ul>
 */
final class BoardView extends Canvas {
    enum Overlay { NONE, TEACHING, HEATMAP }

    private static final Color BACKGROUND = Color.web("#3b2a2a");
    private static final Color TILE = Color.web("#fdf6ec");
    private static final Color TILE_TEXT = Color.web("#2d2420");
    private static final Color BLANK = Color.web("#9c7f4e");
    private static final Color GOOD = Color.web("#d9f2dd");
    private static final Color BAD = Color.web("#f4a259");
    private static final Color CONFLICT = Color.web("#d7263d");
    private static final Color HEAT_LOW = Color.web("#fff7e6");
    private static final Color HEAT_HIGH = Color.web("#d7263d");

    private Board board;
    private Goal goal;
    private Overlay overlay = Overlay.NONE;
    private long[] heat;

    BoardView(double size) {
        super(size, size);
        redraw();
    }

    void show(Board board, Goal goal) {
        this.board = board;
        this.goal = goal;
        redraw();
    }

    void setOverlay(Overlay overlay) {
        this.overlay = overlay;
        redraw();
    }

    void setHeat(long[] counts) {
        this.heat = counts == null ? null : counts.clone();
        redraw();
    }

    Board board() {
        return board;
    }

    void redraw() {
        GraphicsContext g = getGraphicsContext2D();
        double w = getWidth();
        g.setFill(BACKGROUND);
        g.fillRoundRect(0, 0, w, getHeight(), 14, 14);
        if (board == null) return;

        int n = board.size();
        double pad = 6;
        double cell = (w - 2 * pad) / n;
        boolean[] conflicts = overlay == Overlay.TEACHING && goal != null
                ? BasicHeuristics.conflictMask(board, goal) : null;
        long maxHeat = 1;
        if (overlay == Overlay.HEATMAP && heat != null) for (long v : heat) maxHeat = Math.max(maxHeat, v);
        long totalHeat = 0;
        if (heat != null) for (long v : heat) totalHeat += v;

        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        for (int i = 0; i < board.cellCount(); i++) {
            int r = i / n, c = i % n;
            double x = pad + c * cell, y = pad + r * cell;
            int tile = board.tileAt(i);

            if (overlay == Overlay.HEATMAP) {
                long v = heat == null ? 0 : heat[i];
                double ratio = Math.sqrt((double) v / maxHeat);
                g.setFill(HEAT_LOW.interpolate(HEAT_HIGH, ratio));
                g.fillRoundRect(x + 2, y + 2, cell - 4, cell - 4, 8, 8);
                g.setFill(ratio > 0.55 ? Color.WHITE : TILE_TEXT);
                g.setFont(Font.font("System", FontWeight.BOLD, cell / 5));
                double pct = totalHeat == 0 ? 0 : 100.0 * v / totalHeat;
                g.fillText(String.format("%.1f%%", pct), x + cell / 2, y + cell / 2);
                continue;
            }

            if (tile == 0) {
                g.setFill(BLANK);
                g.fillRoundRect(x + 2, y + 2, cell - 4, cell - 4, 8, 8);
                continue;
            }
            int distance = 0;
            if (overlay == Overlay.TEACHING && goal != null) {
                distance = Math.abs(r - goal.rowOf(tile)) + Math.abs(c - goal.colOf(tile));
                double ratio = Math.min(1.0, distance / (double) (2 * (n - 1)));
                g.setFill(distance == 0 ? GOOD : TILE.interpolate(BAD, 0.25 + 0.75 * ratio));
            } else {
                g.setFill(TILE);
            }
            g.fillRoundRect(x + 2, y + 2, cell - 4, cell - 4, 8, 8);
            if (conflicts != null && conflicts[i]) {
                g.setStroke(CONFLICT);
                g.setLineWidth(3);
                g.strokeRoundRect(x + 3.5, y + 3.5, cell - 7, cell - 7, 8, 8);
            }
            g.setFill(TILE_TEXT);
            g.setFont(Font.font("System", FontWeight.BOLD, cell / 2.6));
            g.fillText(String.valueOf(tile), x + cell / 2, y + cell / 2);
            if (overlay == Overlay.TEACHING && distance > 0) {
                g.setFont(Font.font("System", FontWeight.NORMAL, cell / 6.5));
                g.fillText("d=" + distance, x + cell - cell / 5, y + cell / 6);
            }
        }
    }
}
