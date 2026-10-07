package com.example.npuzzleai.learning;

import java.util.Random;

/**
 * Perceptron nhiều lớp tối giản (một lớp ẩn ReLU, đầu ra tuyến tính) huấn luyện bằng Adam trên MSE.
 * Viết bằng Java thuần để solver giữ JVM-native, không cần Python/PyTorch cho bản proof-of-concept.
 */
public final class Mlp {
    final int inputs;
    final int hidden;
    final double[][] w1;
    final double[] b1;
    final double[] w2;
    double b2;

    public Mlp(int inputs, int hidden, long seed) {
        this.inputs = inputs;
        this.hidden = hidden;
        this.w1 = new double[hidden][inputs];
        this.b1 = new double[hidden];
        this.w2 = new double[hidden];
        Random rnd = new Random(seed);
        double s1 = Math.sqrt(2.0 / inputs);
        double s2 = Math.sqrt(1.0 / hidden);
        for (int j = 0; j < hidden; j++) {
            for (int i = 0; i < inputs; i++) w1[j][i] = rnd.nextGaussian() * s1;
            w2[j] = rnd.nextGaussian() * s2;
        }
    }

    Mlp(int inputs, int hidden, double[][] w1, double[] b1, double[] w2, double b2) {
        this.inputs = inputs;
        this.hidden = hidden;
        this.w1 = w1;
        this.b1 = b1;
        this.w2 = w2;
        this.b2 = b2;
    }

    public double predict(double[] x) {
        double out = b2;
        for (int j = 0; j < hidden; j++) {
            double a = b1[j];
            double[] row = w1[j];
            for (int i = 0; i < inputs; i++) a += row[i] * x[i];
            if (a > 0) out += w2[j] * a;
        }
        return out;
    }

    /** Huấn luyện mini-batch Adam; dữ liệu x đã được chuẩn hoá. Trả về MSE của epoch cuối. */
    public double train(double[][] x, double[] y, int epochs, int batchSize, double learningRate, long seed) {
        int n = x.length;
        Random rnd = new Random(seed);
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Adam opt = new Adam(hidden * inputs + hidden + hidden + 1, learningRate);
        double[] grad = new double[opt.size];
        double[] hiddenAct = new double[hidden];
        double lastMse = Double.NaN;
        for (int epoch = 0; epoch < epochs; epoch++) {
            for (int i = n - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                int t = order[i];
                order[i] = order[j];
                order[j] = t;
            }
            double sse = 0;
            for (int start = 0; start < n; start += batchSize) {
                int end = Math.min(n, start + batchSize);
                java.util.Arrays.fill(grad, 0);
                for (int k = start; k < end; k++) {
                    double[] xi = x[order[k]];
                    double out = b2;
                    for (int j = 0; j < hidden; j++) {
                        double a = b1[j];
                        for (int i = 0; i < inputs; i++) a += w1[j][i] * xi[i];
                        hiddenAct[j] = a > 0 ? a : 0;
                        out += w2[j] * hiddenAct[j];
                    }
                    double err = out - y[order[k]];
                    sse += err * err;
                    // Gradient: thứ tự tham số [w1 (hidden×inputs), b1, w2, b2].
                    int p = 0;
                    for (int j = 0; j < hidden; j++) {
                        double back = hiddenAct[j] > 0 ? err * w2[j] : 0;
                        for (int i = 0; i < inputs; i++) grad[p++] += back * xi[i];
                    }
                    for (int j = 0; j < hidden; j++) grad[p++] += hiddenAct[j] > 0 ? err * w2[j] : 0;
                    for (int j = 0; j < hidden; j++) grad[p++] += err * hiddenAct[j];
                    grad[p] += err;
                }
                double scale = 2.0 / (end - start);
                for (int g = 0; g < grad.length; g++) grad[g] *= scale;
                opt.step(this, grad);
            }
            lastMse = sse / n;
        }
        return lastMse;
    }

    /** Bộ tối ưu Adam (Kingma &amp; Ba 2015) trên vector tham số phẳng. */
    private static final class Adam {
        final int size;
        final double lr;
        final double[] m;
        final double[] v;
        int t;

        Adam(int size, double lr) {
            this.size = size;
            this.lr = lr;
            this.m = new double[size];
            this.v = new double[size];
        }

        void step(Mlp net, double[] grad) {
            t++;
            double b1c = 1 - Math.pow(0.9, t);
            double b2c = 1 - Math.pow(0.999, t);
            int p = 0;
            for (int j = 0; j < net.hidden; j++) {
                for (int i = 0; i < net.inputs; i++, p++) net.w1[j][i] -= update(p, grad[p], b1c, b2c);
            }
            for (int j = 0; j < net.hidden; j++, p++) net.b1[j] -= update(p, grad[p], b1c, b2c);
            for (int j = 0; j < net.hidden; j++, p++) net.w2[j] -= update(p, grad[p], b1c, b2c);
            net.b2 -= update(p, grad[p], b1c, b2c);
        }

        private double update(int p, double g, double b1c, double b2c) {
            m[p] = 0.9 * m[p] + 0.1 * g;
            v[p] = 0.999 * v[p] + 0.001 * g * g;
            return lr * (m[p] / b1c) / (Math.sqrt(v[p] / b2c) + 1e-8);
        }
    }
}
