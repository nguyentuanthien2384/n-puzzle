package com.example.npuzzleai.benchmark;

import java.util.Arrays;
import java.util.List;

/**
 * Thống kê mô tả. Với dữ liệu tìm kiếm thường có outlier do GC/lập lịch, báo cáo nên dùng
 * trung vị và phân phối (p10/p90) bên cạnh trung bình.
 */
public final class Statistics {
    private Statistics() {
    }

    public static double mean(double[] v) {
        if (v.length == 0) return Double.NaN;
        double s = 0;
        for (double x : v) s += x;
        return s / v.length;
    }

    public static double median(double[] v) {
        return percentile(v, 50);
    }

    /** Phân vị bằng nội suy tuyến tính giữa hai hạng gần nhất. */
    public static double percentile(double[] v, double p) {
        if (v.length == 0) return Double.NaN;
        double[] s = v.clone();
        Arrays.sort(s);
        if (s.length == 1) return s[0];
        double rank = p / 100.0 * (s.length - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        return s[lo] + (s[hi] - s[lo]) * (rank - lo);
    }

    /** Độ lệch chuẩn mẫu (chia n-1). */
    public static double stddev(double[] v) {
        if (v.length < 2) return 0;
        double m = mean(v);
        double s = 0;
        for (double x : v) s += (x - m) * (x - m);
        return Math.sqrt(s / (v.length - 1));
    }

    public static double[] toArray(List<? extends Number> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < out.length; i++) out[i] = values.get(i).doubleValue();
        return out;
    }
}
