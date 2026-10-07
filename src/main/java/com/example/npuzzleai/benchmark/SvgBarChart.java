package com.example.npuzzleai.benchmark;

import java.util.List;
import java.util.Locale;

/**
 * Biểu đồ cột ngang dạng SVG thuần (không dependency) - đưa thẳng vào báo cáo/paper.
 * Hỗ trợ trục log để so sánh số node chênh nhau nhiều bậc độ lớn.
 */
public final class SvgBarChart {
    private static final int WIDTH = 900;
    private static final int LABEL_WIDTH = 300;
    private static final int ROW = 28;
    private static final int TOP = 56;

    private SvgBarChart() {
    }

    public static String horizontal(String title, String unit, List<String> labels, List<Double> values, boolean logScale) {
        int n = labels.size();
        int height = TOP + n * ROW + 40;
        double max = 0;
        for (Double v : values) if (v != null && !v.isNaN() && v > max) max = v;
        double scaleMax = logScale ? Math.log10(Math.max(10, max) + 1) : Math.max(max, 1e-9);
        int barArea = WIDTH - LABEL_WIDTH - 120;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\" "
                        + "font-family=\"Segoe UI, Arial, sans-serif\" font-size=\"13\">%n", WIDTH, height, WIDTH, height));
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");
        sb.append(String.format(Locale.ROOT, "<text x=\"16\" y=\"28\" font-size=\"17\" font-weight=\"600\" fill=\"#1f2937\">%s</text>%n",
                escape(title)));
        sb.append(String.format(Locale.ROOT, "<text x=\"16\" y=\"46\" fill=\"#6b7280\">%s%s</text>%n",
                escape(unit), logScale ? " (thang log)" : ""));
        for (int i = 0; i < n; i++) {
            int y = TOP + i * ROW;
            Double v = values.get(i);
            sb.append(String.format(Locale.ROOT, "<text x=\"%d\" y=\"%d\" text-anchor=\"end\" fill=\"#374151\">%s</text>%n",
                    LABEL_WIDTH - 10, y + 18, escape(labels.get(i))));
            if (v == null || v.isNaN()) {
                sb.append(String.format(Locale.ROOT, "<text x=\"%d\" y=\"%d\" fill=\"#9ca3af\">không có dữ liệu</text>%n",
                        LABEL_WIDTH, y + 18));
                continue;
            }
            double ratio = logScale ? Math.log10(v + 1) / scaleMax : v / scaleMax;
            int w = (int) Math.max(1, Math.round(ratio * barArea));
            sb.append(String.format(Locale.ROOT,
                    "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"3\" fill=\"#2b7de9\"/>%n",
                    LABEL_WIDTH, y + 5, w, ROW - 10));
            sb.append(String.format(Locale.ROOT, "<text x=\"%d\" y=\"%d\" fill=\"#111827\">%s</text>%n",
                    LABEL_WIDTH + w + 6, y + 18, format(v)));
        }
        sb.append("</svg>\n");
        return sb.toString();
    }

    private static String format(double v) {
        if (v >= 1e6) return String.format(Locale.ROOT, "%.2fM", v / 1e6);
        if (v >= 1e4) return String.format(Locale.ROOT, "%.1fk", v / 1e3);
        if (v == Math.rint(v)) return String.format(Locale.ROOT, "%.0f", v);
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
