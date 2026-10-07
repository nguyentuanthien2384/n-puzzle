package com.example.npuzzleai.ui;

import com.example.npuzzleai.algorithms.AlgorithmRegistry;
import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.PuzzleProblem;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.SearchAlgorithm;
import com.example.npuzzleai.search.SearchBudget;
import com.example.npuzzleai.search.SearchObserver;
import com.example.npuzzleai.search.SearchResult;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Compare Lab: chạy nhiều tổ hợp thuật toán × heuristic trên <i>cùng</i> trạng thái, đích, timeout
 * và ngân sách bộ nhớ; tuần tự để các lần chạy không tranh CPU với nhau.
 */
final class CompareTab {
    record Row(String combo, String status, int length, String optimal, long expanded, long generated,
               long maxOpen, double timeMs, long heuristicCalls, boolean claimsOptimal) {
    }

    private final BorderPane root = new BorderPane();
    private final Supplier<Board> boardSource;
    private final Supplier<Goal> goalSource;
    private final List<CheckBox> algoBoxes = new ArrayList<>();
    private final List<CheckBox> heuristicBoxes = new ArrayList<>();
    private final Spinner<Integer> timeout = UiSupport.intSpinner(1, 3600, 30, 5);
    private final Spinner<Integer> memoryMb = UiSupport.intSpinner(0, 65_536, 1024, 128);
    private final CheckBox logScale = new CheckBox("Thang log10");
    private final Button runBtn = new Button("Chạy so sánh");
    private final Button cancelBtn = new Button("Dừng");
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = new Label();
    private final Label boardLabel = new Label();
    private final TableView<Row> table = new TableView<>();
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final BarChart<String, Number> expandedChart = chart("Node mở rộng");
    private final BarChart<String, Number> timeChart = chart("Thời gian (ms)");
    private volatile boolean cancelRequested;

    CompareTab(Supplier<Board> boardSource, Supplier<Goal> goalSource) {
        this.boardSource = boardSource;
        this.goalSource = goalSource;
        build();
    }

    Node node() {
        return root;
    }

    private void build() {
        root.setPadding(new Insets(10));
        Button syncBtn = new Button("Dùng bảng ở tab Giải");
        syncBtn.setOnAction(e -> refreshBoardLabel());
        refreshBoardLabel();

        VBox top = new VBox(6,
                new HBox(8, UiSupport.title("Bảng:"), boardLabel, syncBtn),
                UiSupport.title("Thuật toán"),
                UiSupport.checkBoxes(AlgorithmRegistry.defaults().defaultSpecs(), Set.of("astar", "ida", "rbfs", "wastar:1.5"), algoBoxes),
                UiSupport.title("Heuristic"),
                UiSupport.checkBoxes(HeuristicRegistry.defaults().defaultIds(), Set.of("manhattan", "linear-conflict", "apdb"), heuristicBoxes),
                new HBox(10, new Label("Timeout (giây)"), timeout, new Label("Bộ nhớ (MB, 0 = ∞)"), memoryMb,
                        runBtn, cancelBtn, progress, status),
                UiSupport.hint("Cột 'Tối ưu?' so với độ dài ngắn nhất tìm được bởi tổ hợp có cam kết tối ưu "
                        + "(thuật toán tối ưu + heuristic khai báo admissible). Thuật toán không dùng heuristic chỉ chạy một lần."));
        ((HBox) top.getChildren().get(5)).setAlignment(Pos.CENTER_LEFT);
        cancelBtn.setDisable(true);
        progress.setPrefWidth(160);
        root.setTop(top);

        table.setItems(rows);
        table.getColumns().add(UiSupport.column("Tổ hợp", Row::combo, 230));
        table.getColumns().add(UiSupport.column("Trạng thái", Row::status, 120));
        table.getColumns().add(UiSupport.column("Độ dài", Row::length, 70));
        table.getColumns().add(UiSupport.column("Tối ưu?", Row::optimal, 90));
        table.getColumns().add(UiSupport.column("Expanded", Row::expanded, 110));
        table.getColumns().add(UiSupport.column("Generated", Row::generated, 110));
        table.getColumns().add(UiSupport.column("Max OPEN", Row::maxOpen, 100));
        table.getColumns().add(UiSupport.column("Thời gian (ms)", r -> Math.round(r.timeMs() * 100) / 100.0, 110));
        table.getColumns().add(UiSupport.column("Lần gọi h", Row::heuristicCalls, 110));
        table.setPlaceholder(new Label("Chọn tổ hợp rồi bấm 'Chạy so sánh'"));
        BorderPane.setMargin(table, new Insets(10, 0, 10, 0));
        root.setCenter(table);

        HBox charts = new HBox(10, expandedChart, timeChart, logScale);
        HBox.setHgrow(expandedChart, Priority.ALWAYS);
        HBox.setHgrow(timeChart, Priority.ALWAYS);
        charts.setPrefHeight(260);
        root.setBottom(charts);

        runBtn.setOnAction(e -> run());
        cancelBtn.setOnAction(e -> cancelRequested = true);
        logScale.setOnAction(e -> redrawCharts());
    }

    private static BarChart<String, Number> chart(String title) {
        BarChart<String, Number> c = new BarChart<>(new CategoryAxis(), new NumberAxis());
        c.setTitle(title);
        c.setLegendVisible(false);
        c.setAnimated(false);
        return c;
    }

    private void refreshBoardLabel() {
        Board b = boardSource.get();
        boardLabel.setText(b + "   (đích " + goalSource.get() + ")");
    }

    private void run() {
        Board start = boardSource.get();
        Goal goal = goalSource.get();
        refreshBoardLabel();
        List<String> algos = UiSupport.selected(algoBoxes);
        List<String> hs = UiSupport.selected(heuristicBoxes);
        if (algos.isEmpty()) {
            status.setText("Chọn ít nhất một thuật toán");
            return;
        }
        SearchBudget budget = new SearchBudget(timeout.getValue() * 1000L, 0, memoryMb.getValue() * 1024L * 1024L);
        rows.clear();
        cancelRequested = false;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                List<SearchAlgorithm> algorithms = new ArrayList<>();
                for (String a : algos) algorithms.add(AlgorithmRegistry.defaults().create(a));
                int total = 0;
                for (SearchAlgorithm a : algorithms) total += a.properties().usesHeuristic() ? Math.max(1, hs.size()) : 1;
                int done = 0;
                for (SearchAlgorithm algo : algorithms) {
                    List<String> hList = algo.properties().usesHeuristic() ? hs : List.of("zero");
                    for (String hId : hList) {
                        if (cancelRequested) return null;
                        Heuristic h = HeuristicRegistry.defaults().create(hId);
                        String combo = algo.properties().usesHeuristic() ? algo.id() + " + " + hId : algo.id();
                        if (algo.properties().usesHeuristic() && !h.supports(start.size())) {
                            add(new Row(combo, "Không hỗ trợ kích thước", -1, "-", 0, 0, 0, 0, 0, false));
                        } else {
                            if (algo.properties().usesHeuristic() && h.properties().requiresPreprocessing()) {
                                updateMessage("Tiền xử lý " + hId + "...");
                                h.prepare(goal);
                            }
                            updateMessage("Đang chạy " + combo + "...");
                            SearchResult r = algo.solve(new PuzzleProblem(start, goal), h, budget,
                                    SearchObserver.cancellable(() -> cancelRequested));
                            boolean claims = algo.properties().optimalWithAdmissibleHeuristic()
                                    && (!algo.properties().usesHeuristic() || h.properties().claimedAdmissible());
                            add(new Row(combo, r.status().label(), r.length(), "", r.metrics().expanded,
                                    r.metrics().generated, r.metrics().maxOpen, r.metrics().wallTimeMillis(),
                                    r.metrics().heuristicCalls, claims));
                        }
                        updateProgress(++done, total);
                    }
                }
                return null;
            }

            private void add(Row row) {
                Platform.runLater(() -> {
                    rows.add(row);
                    markOptimality();
                    redrawCharts();
                });
            }
        };
        progress.progressProperty().bind(task.progressProperty());
        status.textProperty().bind(task.messageProperty());
        Runnable done = () -> {
            progress.progressProperty().unbind();
            status.textProperty().unbind();
            status.setText(cancelRequested ? "Đã dừng" : "Hoàn tất " + rows.size() + " tổ hợp");
            runBtn.setDisable(false);
            cancelBtn.setDisable(true);
        };
        task.setOnSucceeded(e -> done.run());
        task.setOnFailed(e -> {
            done.run();
            UiSupport.error("Lỗi khi so sánh", task.getException());
        });
        runBtn.setDisable(true);
        cancelBtn.setDisable(false);
        Thread t = new Thread(task, "search-lab-compare");
        t.setDaemon(true);
        t.start();
    }

    /** Đánh dấu cột "Tối ưu?" theo độ dài ngắn nhất của các tổ hợp có cam kết tối ưu. */
    private void markOptimality() {
        int reference = Integer.MAX_VALUE;
        for (Row r : rows) if (r.claimsOptimal() && r.length() >= 0) reference = Math.min(reference, r.length());
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            String mark;
            if (r.length() < 0 || reference == Integer.MAX_VALUE) mark = "-";
            else if (r.length() == reference) mark = "✓ tối ưu";
            else mark = String.format("×%.2f", (double) r.length() / Math.max(1, reference));
            if (!mark.equals(r.optimal())) {
                rows.set(i, new Row(r.combo(), r.status(), r.length(), mark, r.expanded(), r.generated(), r.maxOpen(),
                        r.timeMs(), r.heuristicCalls(), r.claimsOptimal()));
            }
        }
    }

    private void redrawCharts() {
        boolean log = logScale.isSelected();
        XYChart.Series<String, Number> exp = new XYChart.Series<>();
        XYChart.Series<String, Number> time = new XYChart.Series<>();
        for (Row r : rows) {
            if (r.length() < 0) continue;
            exp.getData().add(new XYChart.Data<>(r.combo(), log ? Math.log10(r.expanded() + 1) : r.expanded()));
            time.getData().add(new XYChart.Data<>(r.combo(), log ? Math.log10(r.timeMs() + 1) : r.timeMs()));
        }
        expandedChart.getYAxis().setLabel(log ? "log10(node)" : "node");
        timeChart.getYAxis().setLabel(log ? "log10(ms)" : "ms");
        expandedChart.getData().setAll(List.of(exp));
        timeChart.getData().setAll(List.of(time));
    }
}
