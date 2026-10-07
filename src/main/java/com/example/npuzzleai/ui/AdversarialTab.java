package com.example.npuzzleai.ui;

import com.example.npuzzleai.algorithms.AlgorithmRegistry;
import com.example.npuzzleai.benchmark.Datasets;
import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.research.AdversarialGenerator;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Sinh puzzle đối kháng: tiến hoá tìm trạng thái khiến heuristic đánh giá kém (h* − h lớn) hoặc khiến
 * solver mở rất nhiều node - minh hoạ benchmark trung bình chưa đủ. Kết quả gửi được sang tab Giải
 * hoặc lưu thành dataset cho Experiment Manager.
 */
final class AdversarialTab {
    private final BorderPane root = new BorderPane();
    private final BiConsumer<Board, Goal> sendToSolver;
    private final ComboBox<Integer> sizeBox = new ComboBox<>(FXCollections.observableArrayList(3, 4));
    private final ComboBox<String> goalBox = new ComboBox<>(FXCollections.observableArrayList(UiSupport.GOAL_NAMES));
    private final ComboBox<AdversarialGenerator.Objective> objectiveBox =
            new ComboBox<>(FXCollections.observableArrayList(AdversarialGenerator.Objective.values()));
    private final ComboBox<String> algoBox = new ComboBox<>(FXCollections.observableArrayList(AlgorithmRegistry.defaults().defaultSpecs()));
    private final ComboBox<String> heuristicBox = new ComboBox<>(FXCollections.observableArrayList(HeuristicRegistry.defaults().defaultIds()));
    private final Spinner<Integer> population = UiSupport.intSpinner(4, 500, 30, 5);
    private final Spinner<Integer> generations = UiSupport.intSpinner(1, 1000, 20, 5);
    private final Spinner<Integer> mutation = UiSupport.intSpinner(1, 50, 6, 1);
    private final Spinner<Integer> nodeBudget = UiSupport.intSpinner(1_000, 50_000_000, 200_000, 50_000);
    private final TextField seedField = new TextField(String.valueOf(Datasets.DEFAULT_SEED));
    private final Button runBtn = new Button("Bắt đầu tiến hoá");
    private final Button cancelBtn = new Button("Dừng");
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = new Label();
    private final LineChart<Number, Number> chart = new LineChart<>(new NumberAxis(), new NumberAxis());
    private final XYChart.Series<Number, Number> bestSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> meanSeries = new XYChart.Series<>();
    private final TableView<AdversarialGenerator.Candidate> table = new TableView<>();
    private volatile boolean cancelRequested;
    private Goal lastGoal;
    private long lastSeed;

    AdversarialTab(BiConsumer<Board, Goal> sendToSolver) {
        this.sendToSolver = sendToSolver;
        build();
    }

    Node node() {
        return root;
    }

    private void build() {
        root.setPadding(new Insets(10));
        sizeBox.setValue(3);
        goalBox.setValue(Goal.STANDARD);
        objectiveBox.setValue(AdversarialGenerator.Objective.HEURISTIC_GAP);
        algoBox.setValue("astar");
        heuristicBox.setValue("linear-conflict");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        int r = 0;
        grid.addRow(r++, new Label("Kích thước"), sizeBox);
        grid.addRow(r++, new Label("Đích"), goalBox);
        grid.addRow(r++, new Label("Mục tiêu"), objectiveBox);
        grid.addRow(r++, new Label("Thuật toán (đếm node)"), algoBox);
        grid.addRow(r++, new Label("Heuristic bị kiểm tra"), heuristicBox);
        grid.addRow(r++, new Label("Quần thể μ"), population);
        grid.addRow(r++, new Label("Số thế hệ"), generations);
        grid.addRow(r++, new Label("Đột biến tối đa (nước)"), mutation);
        grid.addRow(r++, new Label("Trần node / đánh giá"), nodeBudget);
        grid.addRow(r, new Label("Seed"), seedField);

        Button sendBtn = new Button("Mở trong tab Giải");
        sendBtn.setOnAction(e -> {
            AdversarialGenerator.Candidate c = table.getSelectionModel().getSelectedItem();
            if (c != null && lastGoal != null) sendToSolver.accept(c.board(), lastGoal);
        });
        Button saveBtn = new Button("Lưu thành dataset...");
        saveBtn.setOnAction(e -> saveDataset());

        VBox form = new VBox(8, UiSupport.title("Cấu hình tiến hoá"), grid,
                UiSupport.hint("HEURISTIC_GAP: tìm trạng thái có h* − h lớn nhất (heuristic đánh giá thấp nhất). "
                        + "EXPANSIONS: tìm trạng thái khiến thuật toán mở nhiều node nhất. "
                        + "Đột biến luôn giữ tính khả giải."),
                new HBox(8, runBtn, cancelBtn), progress, status,
                new Separator(), new HBox(8, sendBtn, saveBtn));
        form.setPrefWidth(400);
        progress.setPrefWidth(360);
        cancelBtn.setDisable(true);
        root.setLeft(form);

        chart.setTitle("Fitness theo thế hệ");
        chart.getXAxis().setLabel("thế hệ");
        chart.getYAxis().setLabel("fitness");
        chart.setAnimated(false);
        bestSeries.setName("tốt nhất");
        meanSeries.setName("trung bình quần thể");
        chart.getData().addAll(List.of(bestSeries, meanSeries));

        table.getColumns().add(UiSupport.column("Fitness", c -> Math.round(c.fitness()), 80));
        table.getColumns().add(UiSupport.column("h", AdversarialGenerator.Candidate::heuristicValue, 60));
        table.getColumns().add(UiSupport.column("h* / độ dài", c -> c.optimalLength() < 0 ? "?" : String.valueOf(c.optimalLength()), 90));
        table.getColumns().add(UiSupport.column("Expanded",
                c -> c.expanded() == 0 ? "-" : String.format("%,d", c.expanded()), 100));
        table.getColumns().add(UiSupport.column("Trạng thái", c -> c.board().toString(), 360));
        table.setPlaceholder(new Label("Chưa có kết quả"));
        VBox center = new VBox(10, chart, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        center.setPadding(new Insets(0, 0, 0, 12));
        root.setCenter(center);

        runBtn.setOnAction(e -> run());
        cancelBtn.setOnAction(e -> cancelRequested = true);
    }

    private void run() {
        long seed;
        try {
            seed = Long.parseLong(seedField.getText().trim());
        } catch (NumberFormatException e) {
            UiSupport.error("Seed phải là số nguyên", e);
            return;
        }
        Goal goal = UiSupport.goal(goalBox.getValue(), sizeBox.getValue());
        AdversarialGenerator.Config config;
        AdversarialGenerator generator;
        try {
            config = new AdversarialGenerator.Config(goal, objectiveBox.getValue(), algoBox.getValue(),
                    heuristicBox.getValue(), population.getValue(), generations.getValue(), mutation.getValue(),
                    nodeBudget.getValue(), seed);
            generator = new AdversarialGenerator(config);
        } catch (IllegalArgumentException ex) {
            UiSupport.error("Cấu hình không hợp lệ", ex);
            return;
        }
        lastGoal = goal;
        lastSeed = seed;
        cancelRequested = false;
        bestSeries.getData().clear();
        meanSeries.getData().clear();
        table.getItems().clear();

        Task<List<AdversarialGenerator.Candidate>> task = new Task<>() {
            @Override
            protected List<AdversarialGenerator.Candidate> call() {
                updateMessage("Khởi tạo quần thể...");
                return generator.run((gen, best, mean) -> {
                    updateProgress(gen, config.generations());
                    updateMessage(String.format(Locale.ROOT, "Thế hệ %d/%d: tốt nhất %.0f, TB %.1f",
                            gen, config.generations(), best.fitness(), mean));
                    Platform.runLater(() -> {
                        bestSeries.getData().add(new XYChart.Data<>(gen, best.fitness()));
                        meanSeries.getData().add(new XYChart.Data<>(gen, mean));
                    });
                }, () -> cancelRequested);
            }
        };
        progress.progressProperty().bind(task.progressProperty());
        status.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> {
            done();
            table.setItems(FXCollections.observableArrayList(task.getValue()));
            status.setText("Đã đánh giá " + generator.evaluations() + " trạng thái" + (cancelRequested ? " (đã dừng)" : ""));
        });
        task.setOnFailed(e -> {
            done();
            UiSupport.error("Tiến hoá thất bại", task.getException());
        });
        runBtn.setDisable(true);
        cancelBtn.setDisable(false);
        Thread t = new Thread(task, "search-lab-adversarial");
        t.setDaemon(true);
        t.start();
    }

    private void done() {
        progress.progressProperty().unbind();
        status.textProperty().unbind();
        runBtn.setDisable(false);
        cancelBtn.setDisable(true);
    }

    private void saveDataset() {
        if (table.getItems().isEmpty() || lastGoal == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName("adversarial.txt");
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) return;
        try {
            Datasets.write(AdversarialGenerator.toDataset("adversarial", lastGoal, table.getItems(), lastSeed), file.toPath());
            status.setText("Đã lưu " + file.getName() + " - dùng trong tab Thí nghiệm qua 'file:" + file.getAbsolutePath() + "'");
        } catch (java.io.IOException ex) {
            UiSupport.error("Không ghi được file", ex);
        }
    }

}
