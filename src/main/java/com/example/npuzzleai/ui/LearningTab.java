package com.example.npuzzleai.ui;

import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.learning.FeatureExtractor;
import com.example.npuzzleai.learning.HeuristicTrainer;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Học heuristic bằng mạng nơ-ron: huấn luyện (3x3 trên h* chính xác, 4x4 trên lời giải IDA* + PDB),
 * xem sai số và mức đánh giá vượt trên tập kiểm định, lưu mô hình để dùng qua {@code learned:file}
 * hoặc Focal Search.
 */
final class LearningTab {
    private final BorderPane root = new BorderPane();
    private final ComboBox<Integer> sizeBox = new ComboBox<>(FXCollections.observableArrayList(3, 4));
    private final ComboBox<String> goalBox = new ComboBox<>(FXCollections.observableArrayList(UiSupport.GOAL_NAMES));
    private final Spinner<Integer> instances = UiSupport.intSpinner(20, 5000, 300, 50);
    private final Spinner<Integer> hidden = UiSupport.intSpinner(2, 256, 16, 4);
    private final Spinner<Integer> epochs = UiSupport.intSpinner(1, 500, 25, 5);
    private final CheckBox pdbFeature = new CheckBox("Thêm đặc trưng additive PDB (4x4)");
    private final Button trainBtn = new Button("Huấn luyện");
    private final Button saveBtn = new Button("Lưu mô hình...");
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = new Label();
    private final TextArea report = new TextArea();
    private final ScatterChart<Number, Number> chart = new ScatterChart<>(new NumberAxis(), new NumberAxis());
    private HeuristicTrainer.Report last;

    LearningTab() {
        build();
    }

    Node node() {
        return root;
    }

    private void build() {
        root.setPadding(new Insets(10));
        sizeBox.setValue(3);
        goalBox.setValue(Goal.STANDARD);
        pdbFeature.setSelected(true);
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        int r = 0;
        grid.addRow(r++, new Label("Kích thước"), sizeBox);
        grid.addRow(r++, new Label("Đích"), goalBox);
        grid.addRow(r++, new Label("Số bài giải (4x4)"), instances);
        grid.addRow(r++, new Label("Nơ-ron lớp ẩn"), hidden);
        grid.addRow(r++, new Label("Số epoch"), epochs);
        grid.add(pdbFeature, 1, r);
        saveBtn.setDisable(true);
        report.setEditable(false);
        report.getStyleClass().add("mono");
        report.setPrefRowCount(12);
        VBox form = new VBox(8, UiSupport.title("Huấn luyện heuristic mạng nơ-ron"), grid,
                UiSupport.hint("3x3: học trên h* chính xác của toàn bộ không gian. 4x4: giải tối ưu các bài đi ngẫu nhiên "
                        + "bằng IDA* + PDB để lấy nhãn (mất vài chục giây). Heuristic học được KHÔNG admissible - dùng "
                        + "với Focal Search (focal:w) để vẫn giữ cận độ dài ≤ w × tối ưu."),
                new HBox(8, trainBtn, saveBtn), progress, status, UiSupport.title("Báo cáo"), report);
        form.setPrefWidth(420);
        progress.setPrefWidth(380);
        VBox.setVgrow(report, Priority.ALWAYS);
        root.setLeft(form);

        chart.setTitle("Dự đoán so với khoảng cách thật (tập kiểm định)");
        chart.getXAxis().setLabel("h* thật");
        chart.getYAxis().setLabel("h dự đoán");
        chart.setAnimated(false);
        BorderPane.setMargin(chart, new Insets(0, 0, 0, 12));
        root.setCenter(chart);

        trainBtn.setOnAction(e -> train());
        saveBtn.setOnAction(e -> save());
    }

    private void train() {
        int size = sizeBox.getValue();
        Goal goal = UiSupport.goal(goalBox.getValue(), size);
        HeuristicTrainer.Options defaults = HeuristicTrainer.Options.defaults();
        HeuristicTrainer.Options options = new HeuristicTrainer.Options(hidden.getValue(), epochs.getValue(),
                defaults.batchSize(), defaults.learningRate(), defaults.maxSamples(), defaults.seed());
        int count = instances.getValue();
        FeatureExtractor.FeatureSet set = pdbFeature.isSelected() ? FeatureExtractor.FeatureSet.BASIC_PDB
                : FeatureExtractor.FeatureSet.BASIC;
        Task<HeuristicTrainer.Report> task = new Task<>() {
            @Override
            protected HeuristicTrainer.Report call() {
                if (size <= 3) {
                    updateMessage("Huấn luyện trên h* chính xác...");
                    return HeuristicTrainer.trainExact(goal, options);
                }
                updateMessage("Giải tối ưu " + count + " bài để lấy nhãn...");
                return HeuristicTrainer.trainFromSolver(goal, count, 20, 70, set, options, n -> {
                    updateProgress(n, count);
                    updateMessage("Đã giải " + n + "/" + count + " bài");
                });
            }
        };
        progress.progressProperty().bind(task.progressProperty());
        status.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> {
            done();
            show(task.getValue());
        });
        task.setOnFailed(e -> {
            done();
            UiSupport.error("Huấn luyện thất bại", task.getException());
        });
        trainBtn.setDisable(true);
        Thread t = new Thread(task, "search-lab-training");
        t.setDaemon(true);
        t.start();
    }

    private void done() {
        progress.progressProperty().unbind();
        status.textProperty().unbind();
        progress.setProgress(1);
        trainBtn.setDisable(false);
    }

    private void show(HeuristicTrainer.Report r) {
        last = r;
        saveBtn.setDisable(false);
        status.setText("Huấn luyện xong trong " + r.millis() + " ms");
        report.setText("Dữ liệu       : " + r.model().trainedOn() + "\n"
                + "Đặc trưng     : " + r.model().featureSet() + "\n"
                + String.format("Mẫu train     : %,d%nMẫu kiểm định : %,d%n", r.trainSamples(), r.validationSamples())
                + String.format("RMSE          : %.3f%nMAE           : %.3f%n", r.validationRmse(), r.validationMae())
                + String.format("Tỉ lệ h > h*  : %.1f%%  (vượt trung bình %.2f bước)%n", r.overestimateRate() * 100, r.meanOverestimate())
                + "\nMô hình hiện có thể dùng ngay với mã heuristic 'learned' (tự huấn luyện lại khi cần)\n"
                + "hoặc lưu file rồi dùng 'learned:<đường dẫn>'. Focal Search đọc mặc định models/learned-NxN.model.");
        XYChart.Series<Number, Number> points = new XYChart.Series<>();
        points.setName("mẫu kiểm định");
        for (double[] p : r.validationPairs()) if (p != null) points.getData().add(new XYChart.Data<>(p[0], p[1]));
        XYChart.Series<Number, Number> ideal = new XYChart.Series<>();
        ideal.setName("h = h*");
        double max = 0;
        for (double[] p : r.validationPairs()) if (p != null) max = Math.max(max, Math.max(p[0], p[1]));
        for (int d = 0; d <= (int) Math.ceil(max); d += Math.max(1, (int) (max / 30))) ideal.getData().add(new XYChart.Data<>(d, d));
        chart.getData().setAll(java.util.List.of(points, ideal));
    }

    private void save() {
        if (last == null) return;
        FileChooser chooser = new FileChooser();
        File dir = Path.of("models").toFile();
        if (dir.isDirectory()) chooser.setInitialDirectory(dir);
        chooser.setInitialFileName("learned-" + last.model().size() + "x" + last.model().size() + ".model");
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            last.model().save(file.toPath());
            HeuristicRegistry.defaults().create("learned:" + file.getAbsolutePath());
            status.setText("Đã lưu " + file.getName() + " - dùng: learned:" + file.getAbsolutePath());
        } catch (IOException ex) {
            UiSupport.error("Không lưu được mô hình", ex);
        }
    }
}
