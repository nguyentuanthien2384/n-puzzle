package com.example.npuzzleai.learning;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mô hình đã huấn luyện: bộ đặc trưng + tham số chuẩn hoá + mạng {@link Mlp}.
 * Lưu dưới dạng văn bản (dễ kiểm tra, đưa vào repo) - mỗi dòng "khoá giá trị...".
 *
 * <p>Với {@code residual = true} (mặc định khi huấn luyện), mạng chỉ học phần dư h* − Manhattan và
 * dự đoán = Manhattan + mạng. Gần đích (Manhattan nhỏ, phần dư ≈ 0) dự đoán bám sát thực tế thay vì
 * ngoại suy kém ở vùng hiếm mẫu.</p>
 */
public final class LearnedModel {
    private static final String MAGIC = "npuzzle-learned-model v1";

    private final int size;
    private final FeatureExtractor.FeatureSet featureSet;
    private final double[] mean;
    private final double[] std;
    private final Mlp net;
    private final String trainedOn;
    private final boolean residual;
    private transient FeatureExtractor extractor;

    LearnedModel(int size, FeatureExtractor.FeatureSet featureSet, double[] mean, double[] std, Mlp net,
                 String trainedOn, boolean residual) {
        this.size = size;
        this.featureSet = featureSet;
        this.mean = mean;
        this.std = std;
        this.net = net;
        this.trainedOn = trainedOn;
        this.residual = residual;
    }

    /** Mạng học phần dư trên nền Manhattan (đặc trưng số 0). */
    public boolean residual() {
        return residual;
    }

    public int size() {
        return size;
    }

    public FeatureExtractor.FeatureSet featureSet() {
        return featureSet;
    }

    public String trainedOn() {
        return trainedOn;
    }

    FeatureExtractor extractor() {
        if (extractor == null) extractor = new FeatureExtractor(featureSet);
        return extractor;
    }

    public void prepare(Goal goal) {
        extractor().prepare(goal);
    }

    /** Dự đoán số bước còn lại (số thực, có thể vượt h* - không chấp nhận được). */
    public double predict(Board board, Goal goal) {
        return predictFeatures(extractor().extract(board, goal));
    }

    double predictFeatures(double[] raw) {
        double[] x = new double[raw.length];
        for (int i = 0; i < raw.length; i++) x[i] = (raw[i] - mean[i]) / std[i];
        double out = net.predict(x);
        // h* − Manhattan luôn >= 0 (Manhattan chấp nhận được với mọi đích) nên kẹp phần dư không âm:
        // mô hình không bao giờ kém thông tin hơn Manhattan.
        return residual ? raw[0] + Math.max(0, out) : out;
    }

    public void save(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(MAGIC + "\n");
            w.write("size " + size + "\n");
            w.write("features " + featureSet + "\n");
            w.write("trainedOn " + trainedOn.replace('\n', ' ') + "\n");
            w.write("target " + (residual ? "residual-manhattan" : "absolute") + "\n");
            w.write("inputs " + net.inputs + "\n");
            w.write("hidden " + net.hidden + "\n");
            w.write("mean " + join(mean) + "\n");
            w.write("std " + join(std) + "\n");
            for (int j = 0; j < net.hidden; j++) w.write("w1 " + join(net.w1[j]) + "\n");
            w.write("b1 " + join(net.b1) + "\n");
            w.write("w2 " + join(net.w2) + "\n");
            w.write("b2 " + num(net.b2) + "\n");
        }
    }

    public static LearnedModel load(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).trim().equals(MAGIC)) throw new IOException("Không phải file mô hình: " + file);
        int size = 0, inputs = 0, hidden = 0;
        FeatureExtractor.FeatureSet set = FeatureExtractor.FeatureSet.BASIC;
        String trainedOn = "";
        boolean residual = false; // file cũ không có dòng "target" là mô hình tuyệt đối
        double[] mean = null, std = null, b1 = null, w2 = null;
        double b2 = 0;
        List<double[]> w1 = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            int sp = t.indexOf(' ');
            String key = sp < 0 ? t : t.substring(0, sp);
            String rest = sp < 0 ? "" : t.substring(sp + 1).trim();
            switch (key) {
                case "size" -> size = Integer.parseInt(rest);
                case "features" -> set = FeatureExtractor.FeatureSet.valueOf(rest);
                case "trainedOn" -> trainedOn = rest;
                case "target" -> residual = rest.equals("residual-manhattan");
                case "inputs" -> inputs = Integer.parseInt(rest);
                case "hidden" -> hidden = Integer.parseInt(rest);
                case "mean" -> mean = parse(rest);
                case "std" -> std = parse(rest);
                case "w1" -> w1.add(parse(rest));
                case "b1" -> b1 = parse(rest);
                case "w2" -> w2 = parse(rest);
                case "b2" -> b2 = Double.parseDouble(rest);
                default -> throw new IOException("Khoá không hợp lệ: " + key);
            }
        }
        if (mean == null || std == null || b1 == null || w2 == null || w1.size() != hidden
                || mean.length != inputs || b1.length != hidden || w2.length != hidden) {
            throw new IOException("File mô hình thiếu hoặc sai kích thước tham số");
        }
        Mlp net = new Mlp(inputs, hidden, w1.toArray(new double[0][]), b1, w2, b2);
        LearnedModel model = new LearnedModel(size, set, mean, std, net, trainedOn, residual);
        if (model.extractor().dimension() != inputs) throw new IOException("Số đặc trưng không khớp bộ " + set);
        return model;
    }

    private static String join(double[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(num(v[i]));
        }
        return sb.toString();
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.10g", v);
    }

    private static double[] parse(String s) {
        String[] parts = s.split("\\s+");
        double[] v = new double[parts.length];
        for (int i = 0; i < parts.length; i++) v[i] = Double.parseDouble(parts[i]);
        return v;
    }
}
