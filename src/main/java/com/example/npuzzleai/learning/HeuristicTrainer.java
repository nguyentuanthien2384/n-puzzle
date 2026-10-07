package com.example.npuzzleai.learning;

import com.example.npuzzleai.algorithms.IDAStarSearch;
import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.BoardGenerator;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.PuzzleProblem;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.SearchBudget;
import com.example.npuzzleai.search.SearchObserver;
import com.example.npuzzleai.search.SearchResult;
import com.example.npuzzleai.verify.ExactDistanceTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntConsumer;

/**
 * Pipeline học heuristic: trạng thái đã giải → (đặc trưng, khoảng cách còn lại) → mạng nơ-ron.
 *
 * <ul>
 *   <li>3x3: nhãn chính xác h* từ bảng BFS ngược ({@link ExactDistanceTable}).</li>
 *   <li>4x4: nhãn tối ưu do IDA* + additive PDB giải các trạng thái đi ngẫu nhiên; mỗi trạng thái
 *       trên đường đi tối ưu cũng là một mẫu có nhãn (còn lại = độ dài - vị trí).</li>
 * </ul>
 * Báo cáo gồm RMSE, MAE và tỉ lệ đánh giá vượt (h &gt; h*) trên tập kiểm định tách riêng.
 */
public final class HeuristicTrainer {

    /** Siêu tham số huấn luyện. */
    public record Options(int hidden, int epochs, int batchSize, double learningRate, int maxSamples, long seed) {
        public static Options defaults() {
            return new Options(16, 25, 64, 0.01, 40_000, 20261006L);
        }
    }

    /**
     * Kết quả huấn luyện. {@code validationPairs} chứa tối đa 2.000 cặp (nhãn thật, dự đoán) của tập kiểm định
     * để vẽ biểu đồ phân tán.
     */
    public record Report(LearnedModel model, int trainSamples, int validationSamples, double trainMse,
                         double validationRmse, double validationMae, double overestimateRate,
                         double meanOverestimate, long millis, double[][] validationPairs) {
        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT,
                    "mẫu train=%d, kiểm định=%d, RMSE=%.3f, MAE=%.3f, tỉ lệ h>h*=%.1f%%, vượt TB=%.3f, %d ms",
                    trainSamples, validationSamples, validationRmse, validationMae, overestimateRate * 100,
                    meanOverestimate, millis);
        }
    }

    private HeuristicTrainer() {
    }

    /**
     * Huấn luyện trên 2x2/3x3 với nhãn chính xác.
     *
     * <p>Tập train được <b>lấy mẫu phân tầng theo h*</b>: mỗi mức khoảng cách đóng góp số mẫu ngang nhau
     * (tối đa {@code maxSamples} tổng). Lấy mẫu đều trên toàn không gian chỉ cho vài chục trạng thái
     * gần đích nên mạng ngoại suy rất lệch ở vùng đó. Tập kiểm định vẫn lấy ngẫu nhiên đều (10%, không
     * trùng tập train) để số liệu báo cáo phản ánh phân bố thật.</p>
     */
    public static Report trainExact(Goal goal, Options opt) {
        if (goal.size() > 3) throw new IllegalArgumentException("Nhãn chính xác chỉ có cho tới 3x3");
        ExactDistanceTable table = ExactDistanceTable.forGoal(goal);
        Random rnd = new Random(opt.seed());
        List<List<Integer>> buckets = new ArrayList<>();
        for (int d = 0; d <= table.maxDistance(); d++) buckets.add(new ArrayList<>());
        List<Integer> all = new ArrayList<>();
        for (int r = 0; r < table.permutationCount(); r++) {
            int d = table.distanceAt(r);
            if (d < 0) continue;
            buckets.get(d).add(r);
            all.add(r);
        }
        // Kiểm định: ngẫu nhiên đều.
        java.util.Collections.shuffle(all, rnd);
        int validationSize = Math.max(1, Math.min(all.size() / 10, opt.maxSamples() / 9));
        java.util.Set<Integer> validationRanks = new java.util.HashSet<>(all.subList(0, validationSize));
        // Train: cân bằng theo h* - mỗi mức khoảng cách đúng perBucket mẫu; mức ít trạng thái (gần đích,
        // gần đường kính) được lặp lại (lấy có hoàn lại) để có trọng số ngang các mức đông trạng thái.
        int perBucket = Math.max(1, (opt.maxSamples() - validationSize) / buckets.size());
        List<Board> trainBoards = new ArrayList<>();
        List<Integer> trainLabels = new ArrayList<>();
        for (int d = 0; d < buckets.size(); d++) {
            List<Integer> pool = new ArrayList<>();
            for (int r : buckets.get(d)) if (!validationRanks.contains(r)) pool.add(r);
            if (pool.isEmpty()) continue;
            java.util.Collections.shuffle(pool, rnd);
            List<Board> poolBoards = new ArrayList<>(Math.min(pool.size(), perBucket));
            for (int i = 0; i < Math.min(pool.size(), perBucket); i++) poolBoards.add(table.boardAt(pool.get(i)));
            for (int i = 0; i < perBucket; i++) {
                trainBoards.add(poolBoards.get(i % poolBoards.size()));
                trainLabels.add(d);
            }
        }
        List<Board> valBoards = new ArrayList<>();
        List<Integer> valLabels = new ArrayList<>();
        for (int r : all.subList(0, validationSize)) {
            valBoards.add(table.boardAt(r));
            valLabels.add(table.distanceAt(r));
        }
        return fitSplit(goal, trainBoards, trainLabels, valBoards, valLabels, FeatureExtractor.FeatureSet.BASIC, opt,
                "exact h* " + goal.size() + "x" + goal.size() + " đích " + goal + " (phân tầng theo h*)");
    }

    /**
     * Huấn luyện từ lời giải tối ưu (IDA* + heuristic tham chiếu) của các trạng thái đi ngẫu nhiên.
     *
     * @param instances số bài toán giải để lấy nhãn
     * @param minWalk   số bước đi ngẫu nhiên tối thiểu
     * @param maxWalk   số bước đi ngẫu nhiên tối đa
     * @param progress  nhận số bài đã giải (có thể null)
     */
    public static Report trainFromSolver(Goal goal, int instances, int minWalk, int maxWalk,
                                         FeatureExtractor.FeatureSet set, Options opt, IntConsumer progress) {
        Heuristic reference = HeuristicRegistry.defaults().create(goal.size() <= 5 ? "apdb" : "linear-conflict");
        reference.prepare(goal);
        IDAStarSearch solver = new IDAStarSearch(0);
        Random rnd = new Random(opt.seed());
        List<Board> boards = new ArrayList<>();
        List<Integer> labels = new ArrayList<>();
        int solved = 0;
        for (int i = 0; i < instances; i++) {
            Board start = BoardGenerator.randomWalk(goal, minWalk + rnd.nextInt(Math.max(1, maxWalk - minWalk + 1)), rnd);
            SearchResult r = solver.solve(new PuzzleProblem(start, goal), reference, SearchBudget.ofTimeout(10_000),
                    SearchObserver.NONE);
            if (!r.solved()) continue;
            List<Board> path = r.path();
            int length = r.length();
            for (int k = 0; k < path.size() - 1; k++) {
                boards.add(path.get(k));
                labels.add(length - k);
            }
            solved++;
            if (progress != null) progress.accept(solved);
        }
        if (boards.isEmpty()) throw new IllegalStateException("Không giải được bài nào để lấy nhãn");
        return fit(goal, boards, labels, set, opt, solved + " lời giải tối ưu IDA* " + goal.size() + "x" + goal.size());
    }

    /** Xáo có seed, lấy tối đa {@code maxSamples} mẫu, tách 90% train / 10% kiểm định rồi huấn luyện. */
    static Report fit(Goal goal, List<Board> boards, List<Integer> labels, FeatureExtractor.FeatureSet set,
                      Options opt, String trainedOn) {
        Random rnd = new Random(opt.seed());
        int total = boards.size();
        int[] idx = new int[total];
        for (int i = 0; i < total; i++) idx[i] = i;
        for (int i = total - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = idx[i];
            idx[i] = idx[j];
            idx[j] = t;
        }
        int used = Math.min(total, opt.maxSamples());
        int validation = Math.max(1, used / 10);
        List<Board> trainBoards = new ArrayList<>(), valBoards = new ArrayList<>();
        List<Integer> trainLabels = new ArrayList<>(), valLabels = new ArrayList<>();
        for (int k = 0; k < used; k++) {
            boolean val = k >= used - validation;
            (val ? valBoards : trainBoards).add(boards.get(idx[k]));
            (val ? valLabels : trainLabels).add(labels.get(idx[k]));
        }
        return fitSplit(goal, trainBoards, trainLabels, valBoards, valLabels, set, opt, trainedOn);
    }

    /** Huấn luyện trên tập train cho trước và đánh giá trên tập kiểm định cho trước. */
    static Report fitSplit(Goal goal, List<Board> trainBoards, List<Integer> trainLabels,
                           List<Board> valBoards, List<Integer> valLabels, FeatureExtractor.FeatureSet set,
                           Options opt, String trainedOn) {
        long t0 = System.currentTimeMillis();
        FeatureExtractor fx = new FeatureExtractor(set);
        fx.prepare(goal);
        int train = trainBoards.size();
        int validation = valBoards.size();
        int used = train + validation;
        int dim = fx.dimension();
        double[][] x = new double[used][];
        double[] y = new double[used];
        for (int k = 0; k < used; k++) {
            boolean val = k >= train;
            x[k] = fx.extract(val ? valBoards.get(k - train) : trainBoards.get(k), goal);
            y[k] = val ? valLabels.get(k - train) : trainLabels.get(k);
        }

        double[] mean = new double[dim];
        double[] std = new double[dim];
        for (int k = 0; k < train; k++) for (int d = 0; d < dim; d++) mean[d] += x[k][d];
        for (int d = 0; d < dim; d++) mean[d] /= train;
        for (int k = 0; k < train; k++) for (int d = 0; d < dim; d++) std[d] += (x[k][d] - mean[d]) * (x[k][d] - mean[d]);
        for (int d = 0; d < dim; d++) std[d] = Math.max(1e-6, Math.sqrt(std[d] / train));

        double[][] xs = new double[used][dim];
        for (int k = 0; k < used; k++) for (int d = 0; d < dim; d++) xs[k][d] = (x[k][d] - mean[d]) / std[d];
        // Học phần dư h* − Manhattan (đặc trưng số 0): mạng chỉ cần sửa sai cho một cận dưới đã tốt,
        // và gần đích (phần dư ≈ 0) không bị ngoại suy lệch như khi học giá trị tuyệt đối.
        double[][] xTrain = java.util.Arrays.copyOfRange(xs, 0, train);
        double[] yTrain = new double[train];
        for (int k = 0; k < train; k++) yTrain[k] = y[k] - x[k][0];

        Mlp net = new Mlp(dim, opt.hidden(), opt.seed());
        double mse = net.train(xTrain, yTrain, opt.epochs(), opt.batchSize(), opt.learningRate(), opt.seed());
        LearnedModel model = new LearnedModel(goal.size(), set, mean, std, net, trainedOn, true);

        double se = 0, ae = 0, over = 0, overSum = 0;
        double[][] pairs = new double[Math.min(2000, validation)][];
        for (int k = train; k < used; k++) {
            double pred = Math.round(x[k][0] + Math.max(0, net.predict(xs[k])));
            if (k - train < pairs.length) pairs[k - train] = new double[]{y[k], pred};
            double err = pred - y[k];
            se += err * err;
            ae += Math.abs(err);
            if (err > 0) {
                over++;
                overSum += err;
            }
        }
        return new Report(model, train, validation, mse, Math.sqrt(se / validation), ae / validation,
                over / validation, over == 0 ? 0 : overSum / over, System.currentTimeMillis() - t0, pairs);
    }
}
