package com.example.npuzzleai.learning;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.search.ConfigurableHeuristic;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.HeuristicProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Heuristic học bằng mạng nơ-ron - <b>không</b> khai báo admissible.
 *
 * <p>Không nên đưa h học được vào A* rồi gọi kết quả là tối ưu. Cách dùng an toàn: làm tín hiệu
 * sắp thứ tự trong Focal Search ({@code focal:w}) - cận dưới vẫn dựa trên heuristic đã kiểm định
 * (PDB...), còn mạng nơ-ron chỉ quyết định node nào trong FOCAL được mở rộng trước; hoặc dùng với
 * Weighted A* và báo cáo optimality gap.</p>
 *
 * <p>Nguồn mô hình: file chỉ định ({@code learned:đường_dẫn}); nếu không, file mặc định
 * {@code models/learned-NxN.model} nếu có; nếu không nữa, tự huấn luyện khi {@link #prepare}:
 * 3x3 dùng nhãn h* chính xác (~1 giây), 4x4 dùng lời giải IDA* + PDB của 150 bài đi ngẫu nhiên.</p>
 *
 * <p>Được đăng ký qua ServiceLoader (như một plugin) để gói {@code heuristics} không phụ thuộc
 * ngược vào gói học máy.</p>
 */
public final class LearnedHeuristic implements ConfigurableHeuristic {
    private static final Map<String, LearnedModel> AUTO_MODELS = new ConcurrentHashMap<>();

    private final String id;
    private final Path modelFile;
    private volatile LearnedModel fixedModel;
    private volatile LearnedModel current;
    private volatile Goal currentGoal;

    public LearnedHeuristic() {
        this("learned", null);
    }

    public LearnedHeuristic(String id, Path modelFile) {
        this.id = id;
        this.modelFile = modelFile;
    }

    public static LearnedHeuristic fromFile(Path file) {
        return new LearnedHeuristic("learned:" + file, file);
    }

    /** {@code learned:đường_dẫn} - nạp mô hình từ file. */
    @Override
    public Heuristic configure(String parameter) {
        return fromFile(Path.of(parameter));
    }

    /** Dùng một mô hình có sẵn (ví dụ vừa huấn luyện). */
    public static LearnedHeuristic of(LearnedModel model, String id) {
        LearnedHeuristic h = new LearnedHeuristic(id, null);
        h.fixedModel = model;
        return h;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return "Learned (MLP) " + (modelFile != null ? modelFile.getFileName() : "tự huấn luyện");
    }

    @Override
    public boolean supports(int size) {
        LearnedModel m = fixedModel;
        if (m == null && modelFile != null) m = loadFixed();
        return m != null ? m.size() == size : size >= 3 && size <= 4;
    }

    @Override
    public HeuristicProperties properties() {
        return new HeuristicProperties(false, false, true, true,
                "Mạng nơ-ron học từ lời giải tối ưu - có thể vượt h*, dùng cho Focal/Weighted A*");
    }

    @Override
    public void prepare(Goal goal) {
        modelFor(goal);
    }

    @Override
    public int estimate(Board board, Goal goal) {
        if (goal.isGoal(board)) return 0;
        LearnedModel m = modelFor(goal);
        long v = Math.round(m.predict(board, goal));
        return (int) Math.max(1, v); // không phải đích thì còn ít nhất 1 bước
    }

    private LearnedModel modelFor(Goal goal) {
        LearnedModel m = current;
        if (m != null && goal.equals(currentGoal)) return m;
        synchronized (this) {
            if (current != null && goal.equals(currentGoal)) return current;
            m = fixedModel;
            if (m == null && modelFile != null) m = loadFixed();
            if (m == null) {
                Path def = Path.of("models", "learned-" + goal.size() + "x" + goal.size() + ".model");
                if (Files.isRegularFile(def)) m = load(def);
            }
            if (m == null) m = AUTO_MODELS.computeIfAbsent(goal.size() + "|" + goal.board(), k -> autoTrain(goal));
            if (m.size() != goal.size()) {
                throw new IllegalArgumentException("Mô hình dành cho bảng " + m.size() + "x" + m.size());
            }
            m.prepare(goal);
            current = m;
            currentGoal = goal;
            return m;
        }
    }

    private LearnedModel loadFixed() {
        synchronized (this) {
            if (fixedModel == null) fixedModel = load(modelFile);
            return fixedModel;
        }
    }

    private static LearnedModel load(Path file) {
        try {
            return LearnedModel.load(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Không nạp được mô hình " + file, e);
        }
    }

    private static LearnedModel autoTrain(Goal goal) {
        HeuristicTrainer.Options opt = HeuristicTrainer.Options.defaults();
        if (goal.size() <= 3) return HeuristicTrainer.trainExact(goal, opt).model();
        if (goal.size() == 4) {
            return HeuristicTrainer.trainFromSolver(goal, 150, 20, 70, FeatureExtractor.FeatureSet.BASIC_PDB,
                    opt, null).model();
        }
        throw new IllegalArgumentException("Chưa hỗ trợ tự huấn luyện cho bảng " + goal.size() + "x" + goal.size()
                + " - hãy huấn luyện bằng CLI train-heuristic");
    }
}
