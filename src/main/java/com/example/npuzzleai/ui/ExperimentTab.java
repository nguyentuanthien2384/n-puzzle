package com.example.npuzzleai.ui;

import com.example.npuzzleai.algorithms.AlgorithmRegistry;
import com.example.npuzzleai.benchmark.Dataset;
import com.example.npuzzleai.benchmark.Datasets;
import com.example.npuzzleai.benchmark.ExperimentConfig;
import com.example.npuzzleai.benchmark.ExperimentExporter;
import com.example.npuzzleai.benchmark.ExperimentResult;
import com.example.npuzzleai.benchmark.ExperimentRunner;
import com.example.npuzzleai.benchmark.RunRecord;
import com.example.npuzzleai.benchmark.SummaryRow;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Experiment Manager: chọn dataset, thuật toán, heuristic, số lần lặp, timeout, bộ nhớ, số luồng,
 * seed → chạy và nhận một thư mục thí nghiệm hoàn chỉnh (manifest, CSV, biểu đồ).
 */
final class ExperimentTab {
    private final BorderPane root = new BorderPane();
    private final ComboBox<String> datasetBox = new ComboBox<>(FXCollections.observableArrayList(Datasets.builtInNames().keySet()));
    private final Label datasetInfo = UiSupport.hint("");
    private final TextField seedField = new TextField(String.valueOf(Datasets.DEFAULT_SEED));
    private final TextField nameField = new TextField("thi-nghiem");
    private final List<CheckBox> algoBoxes = new ArrayList<>();
    private final List<CheckBox> heuristicBoxes = new ArrayList<>();
    private final Spinner<Integer> reps = UiSupport.intSpinner(1, 100, 3, 1);
    private final Spinner<Integer> warmup = UiSupport.intSpinner(0, 20, 1, 1);
    private final Spinner<Integer> timeout = UiSupport.intSpinner(1, 3600, 30, 5);
    private final Spinner<Integer> memoryMb = UiSupport.intSpinner(0, 65_536, 0, 256);
    private final Spinner<Integer> threads = UiSupport.intSpinner(1, Runtime.getRuntime().availableProcessors(), 1, 1);
    private final CheckBox shuffle = new CheckBox("Xáo thứ tự chạy (theo seed)");
    private final TextField outputField = new TextField(Path.of("experiments").toAbsolutePath().toString());
    private final Button runBtn = new Button("Chạy thí nghiệm");
    private final Button cancelBtn = new Button("Dừng");
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = new Label();
    private final TextArea log = new TextArea();
    private final TableView<SummaryRow> table = new TableView<>();
    private final BarChart<String, Number> expandedChart = chart("log10(median expanded)");
    private final BarChart<String, Number> timeChart = chart("log10(median ms)");
    private final Label folderLabel = new Label();
    private final Button openBtn = new Button("Mở thư mục kết quả");
    private Path lastFolder;
    private final AtomicBoolean cancelFlag = new AtomicBoolean();

    ExperimentTab() {
        build();
    }

    Node node() {
        return root;
    }

    private void build() {
        root.setPadding(new Insets(10));
        datasetBox.setValue("easy-3x3");
        datasetInfo.setText(Datasets.builtInNames().get("easy-3x3"));
        datasetBox.setOnAction(e -> datasetInfo.setText(Datasets.builtInNames().getOrDefault(datasetBox.getValue(), "")));
        shuffle.setSelected(true);
        Button browse = new Button("Chọn...");
        browse.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            File dir = chooser.showDialog(root.getScene().getWindow());
            if (dir != null) outputField.setText(dir.getAbsolutePath());
        });

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        int r = 0;
        grid.addRow(r++, new Label("Tên"), nameField);
        grid.addRow(r++, new Label("Dataset"), datasetBox);
        grid.add(datasetInfo, 1, r++);
        grid.addRow(r++, new Label("Seed"), seedField);
        grid.addRow(r++, new Label("Số lần lặp"), reps);
        grid.addRow(r++, new Label("Warm-up / tổ hợp"), warmup);
        grid.addRow(r++, new Label("Timeout (giây)"), timeout);
        grid.addRow(r++, new Label("Bộ nhớ (MB, 0 = ∞)"), memoryMb);
        grid.addRow(r++, new Label("Số luồng"), threads);
        grid.add(shuffle, 1, r++);
        grid.addRow(r, new Label("Thư mục"), new HBox(6, outputField, browse));

        VBox form = new VBox(8, UiSupport.title("Cấu hình"), grid,
                UiSupport.title("Thuật toán"),
                UiSupport.checkBoxes(AlgorithmRegistry.defaults().defaultSpecs(), Set.of("astar", "ida"), algoBoxes),
                UiSupport.title("Heuristic"),
                UiSupport.checkBoxes(HeuristicRegistry.defaults().defaultIds(), Set.of("manhattan", "linear-conflict"), heuristicBoxes),
                UiSupport.hint("Số luồng > 1 tăng thông lượng nhưng heap đỉnh/GC không còn quy được cho từng lần chạy "
                        + "(cột tương ứng để trống). Benchmark chuẩn nên dùng 1 luồng."),
                new HBox(8, runBtn, cancelBtn), progress, status);
        form.setPadding(new Insets(0, 12, 0, 0));
        form.setPrefWidth(420);
        progress.setPrefWidth(380);
        cancelBtn.setDisable(true);
        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);
        formScroll.setPrefWidth(440);
        root.setLeft(formScroll);

        table.getColumns().add(UiSupport.column("Tổ hợp", SummaryRow::combo, 200));
        table.getColumns().add(UiSupport.column("Giải", s -> s.solved() + "/" + s.runs(), 70));
        table.getColumns().add(UiSupport.column("Median ms", s -> round(s.medianTimeMs()), 90));
        table.getColumns().add(UiSupport.column("p90 ms", s -> round(s.p90TimeMs()), 80));
        table.getColumns().add(UiSupport.column("Median expanded", s -> Math.round(s.medianExpanded()), 120));
        table.getColumns().add(UiSupport.column("Median maxOpen", s -> Math.round(s.medianMaxOpen()), 110));
        table.getColumns().add(UiSupport.column("Độ dài TB", s -> round(s.meanLength()), 80));
        table.getColumns().add(UiSupport.column("Tối ưu", s -> Double.isNaN(s.optimalRate()) ? "-"
                : Math.round(s.optimalRate() * 100) + "%", 70));
        table.getColumns().add(UiSupport.column("Tiền xử lý ms", s -> round(s.preprocessMs()), 100));
        table.setPlaceholder(new Label("Chưa có kết quả"));

        log.setEditable(false);
        log.setPrefRowCount(8);
        log.getStyleClass().add("mono");
        openBtn.setDisable(true);
        openBtn.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE); // không bị đường dẫn dài bóp nhỏ
        openBtn.setOnAction(e -> openFolder());
        HBox charts = new HBox(10, expandedChart, timeChart);
        HBox.setHgrow(expandedChart, Priority.ALWAYS);
        HBox.setHgrow(timeChart, Priority.ALWAYS);
        charts.setPrefHeight(250);
        HBox folderRow = new HBox(8, openBtn, folderLabel);
        folderRow.setAlignment(Pos.CENTER_LEFT);
        VBox right = new VBox(8, UiSupport.title("Nhật ký"), log, UiSupport.title("Tổng hợp"), table, charts, folderRow);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setCenter(right);

        runBtn.setOnAction(e -> run());
        cancelBtn.setOnAction(e -> cancelFlag.set(true));
    }

    private static BarChart<String, Number> chart(String title) {
        BarChart<String, Number> c = new BarChart<>(new CategoryAxis(), new NumberAxis());
        c.setTitle(title);
        c.setLegendVisible(false);
        c.setAnimated(false);
        return c;
    }

    private void run() {
        long seed;
        try {
            seed = Long.parseLong(seedField.getText().trim());
        } catch (NumberFormatException e) {
            UiSupport.error("Seed phải là số nguyên", e);
            return;
        }
        List<String> algos = UiSupport.selected(algoBoxes);
        if (algos.isEmpty()) {
            status.setText("Chọn ít nhất một thuật toán");
            return;
        }
        ExperimentConfig config = new ExperimentConfig(nameField.getText().trim(), datasetBox.getValue(), algos,
                UiSupport.selected(heuristicBoxes), reps.getValue(), warmup.getValue(), timeout.getValue() * 1000L,
                0, memoryMb.getValue() * 1024L * 1024L, threads.getValue(), seed, shuffle.isSelected(),
                Path.of(outputField.getText().trim()));
        cancelFlag.set(false);
        log.clear();

        Task<Path> task = new Task<>() {
            private ExperimentResult result;

            @Override
            protected Path call() throws Exception {
                updateMessage("Sinh dataset...");
                Dataset dataset = Datasets.resolve(config.dataset(), config.seed());
                append(String.format("Dataset %s: %d instance %dx%d, checksum %s", dataset.name(),
                        dataset.instances().size(), dataset.size(), dataset.size(), Long.toHexString(dataset.checksum())));
                result = new ExperimentRunner().run(config, dataset, new ExperimentRunner.Listener() {
                    @Override
                    public void onRunCompleted(RunRecord r, int completed, int total) {
                        updateProgress(completed, total);
                        updateMessage(completed + "/" + total + " lần chạy");
                        if (!r.solved() || Boolean.FALSE.equals(r.optimalityOk())) {
                            append(r.combo() + " #" + r.instance() + " lần " + r.repetition() + ": " + r.status()
                                    + (Boolean.FALSE.equals(r.optimalityOk()) ? " (SAI TỐI ƯU)" : ""));
                        }
                    }

                    @Override
                    public void onMessage(String message) {
                        append(message);
                    }
                }, cancelFlag);
                updateMessage("Ghi kết quả...");
                return ExperimentExporter.export(result);
            }

            @Override
            protected void succeeded() {
                showSummary(result.summary());
                long failures = result.correctnessFailures();
                append(failures == 0 ? "Không có lỗi tính đúng đắn." : "CẢNH BÁO: " + failures + " lần chạy sai tối ưu/đường đi");
            }

            private void append(String line) {
                Platform.runLater(() -> log.appendText(line + "\n"));
            }
        };
        progress.progressProperty().bind(task.progressProperty());
        status.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> {
            done();
            lastFolder = task.getValue();
            folderLabel.setText(lastFolder.toAbsolutePath().toString());
            openBtn.setDisable(false);
            status.setText(cancelFlag.get() ? "Đã dừng (kết quả một phần đã được ghi)" : "Hoàn tất");
        });
        task.setOnFailed(e -> {
            done();
            UiSupport.error("Thí nghiệm thất bại", task.getException());
        });
        runBtn.setDisable(true);
        cancelBtn.setDisable(false);
        Thread t = new Thread(task, "search-lab-experiment");
        t.setDaemon(true);
        t.start();
    }

    private void done() {
        progress.progressProperty().unbind();
        status.textProperty().unbind();
        runBtn.setDisable(false);
        cancelBtn.setDisable(true);
    }

    private void showSummary(List<SummaryRow> summary) {
        table.setItems(FXCollections.observableArrayList(summary));
        XYChart.Series<String, Number> exp = new XYChart.Series<>();
        XYChart.Series<String, Number> time = new XYChart.Series<>();
        for (SummaryRow s : summary) {
            if (s.solved() == 0) continue;
            exp.getData().add(new XYChart.Data<>(s.combo(), Math.log10(s.medianExpanded() + 1)));
            time.getData().add(new XYChart.Data<>(s.combo(), Math.log10(s.medianTimeMs() + 1)));
        }
        expandedChart.getData().setAll(List.of(exp));
        timeChart.getData().setAll(List.of(time));
    }

    private void openFolder() {
        if (lastFolder == null) return;
        File dir = lastFolder.toFile();
        // Gọi AWT Desktop ngoài JavaFX thread để tránh treo trên một số nền tảng.
        Thread t = new Thread(() -> {
            try {
                if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(dir);
            } catch (Exception ex) {
                Platform.runLater(() -> UiSupport.error("Không mở được thư mục", ex));
            }
        }, "open-folder");
        t.setDaemon(true);
        t.start();
    }

    private static double round(double v) {
        return Double.isNaN(v) ? Double.NaN : Math.round(v * 100) / 100.0;
    }
}
