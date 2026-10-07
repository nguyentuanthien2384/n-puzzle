package com.example.npuzzleai.ui;

import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.verify.ExactDistanceTable;
import com.example.npuzzleai.verify.HeuristicReport;
import com.example.npuzzleai.verify.HeuristicVerifier;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Kiểm định heuristic vét cạn trên 3x3: bảng vi phạm admissibility/consistency kèm phản ví dụ,
 * và biểu đồ trung bình h theo h* (càng sát đường chéo y = x càng chính xác).
 */
final class VerifierTab {
    private final BorderPane root = new BorderPane();
    private final ComboBox<String> goalBox = new ComboBox<>(FXCollections.observableArrayList(UiSupport.GOAL_NAMES));
    private final List<CheckBox> boxes = new ArrayList<>();
    private final Button runBtn = new Button("Kiểm định vét cạn 3x3");
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = new Label();
    private final TableView<HeuristicReport> table = new TableView<>();
    private final LineChart<Number, Number> chart = new LineChart<>(new NumberAxis(), new NumberAxis());
    private final TextArea details = new TextArea();

    VerifierTab() {
        build();
    }

    Node node() {
        return root;
    }

    private void build() {
        root.setPadding(new Insets(10));
        goalBox.setValue(Goal.STANDARD);
        List<String> ids = new ArrayList<>();
        for (Heuristic h : HeuristicRegistry.defaults().all()) if (h.supports(3)) ids.add(h.id());
        HBox actions = new HBox(10, new Label("Đích"), goalBox, runBtn, progress, status);
        actions.setAlignment(Pos.CENTER_LEFT);
        progress.setPrefWidth(180);
        VBox top = new VBox(6,
                UiSupport.title("Heuristic cần kiểm định"),
                UiSupport.checkBoxes(ids, Set.copyOf(ids), boxes),
                actions,
                UiSupport.hint("Dựng h* bằng BFS ngược từ đích cho toàn bộ 181.440 trạng thái tới được, rồi kiểm tra "
                        + "h(s) ≤ h*(s) trên mọi trạng thái và h(s) ≤ 1 + h(s') trên mọi cạnh. Heuristic gắn '?' là thử nghiệm."));
        root.setTop(top);

        table.getColumns().add(UiSupport.column("Heuristic", HeuristicReport::heuristicId, 140));
        table.getColumns().add(UiSupport.column("Khai báo", r -> (r.claimedAdmissible() ? "A" : "") + (r.claimedConsistent() ? "C" : "")
                + (r.experimental() ? "?" : ""), 70));
        table.getColumns().add(UiSupport.column("h > h*", HeuristicReport::admissibilityViolations, 80));
        table.getColumns().add(UiSupport.column("Vượt tối đa", HeuristicReport::maxOverestimate, 85));
        table.getColumns().add(UiSupport.column("Vi phạm nhất quán", HeuristicReport::consistencyViolations, 120));
        table.getColumns().add(UiSupport.column("Chính xác %", r -> pct(r.exactRate()), 90));
        table.getColumns().add(UiSupport.column("TB h", r -> round(r.meanH()), 70));
        table.getColumns().add(UiSupport.column("TB h*−h", r -> round(r.meanError()), 80));
        table.getColumns().add(UiSupport.column("Trung vị h*−h", r -> round(r.medianError()), 100));
        table.getColumns().add(UiSupport.column("Kết luận", HeuristicReport::verdict, 200));
        table.setPlaceholder(new Label("Bấm 'Kiểm định' để chạy"));

        chart.setTitle("Trung bình h theo khoảng cách thật h*");
        chart.getXAxis().setLabel("h* (số bước tối ưu)");
        chart.getYAxis().setLabel("trung bình h");
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        details.setEditable(false);
        details.getStyleClass().add("mono");

        SplitPane bottom = new SplitPane(chart, details);
        bottom.setDividerPositions(0.6);
        SplitPane center = new SplitPane(table, bottom);
        center.setOrientation(javafx.geometry.Orientation.VERTICAL);
        center.setDividerPositions(0.42);
        BorderPane.setMargin(center, new Insets(10, 0, 0, 0));
        root.setCenter(center);

        runBtn.setOnAction(e -> run());
    }

    private void run() {
        Goal goal = UiSupport.goal(goalBox.getValue(), 3);
        List<String> ids = UiSupport.selected(boxes);
        Task<List<HeuristicReport>> task = new Task<>() {
            @Override
            protected List<HeuristicReport> call() {
                updateMessage("Dựng bảng h* (BFS ngược)...");
                ExactDistanceTable table = ExactDistanceTable.forGoal(goal);
                List<HeuristicReport> reports = new ArrayList<>();
                for (int i = 0; i < ids.size(); i++) {
                    if (isCancelled()) break;
                    updateMessage("Kiểm định " + ids.get(i) + "...");
                    reports.add(HeuristicVerifier.verify(HeuristicRegistry.defaults().create(ids.get(i)), table));
                    updateProgress(i + 1, ids.size());
                }
                return reports;
            }
        };
        progress.progressProperty().bind(task.progressProperty());
        status.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> {
            unbind();
            show(task.getValue(), goal);
        });
        task.setOnFailed(e -> {
            unbind();
            UiSupport.error("Lỗi kiểm định", task.getException());
        });
        runBtn.setDisable(true);
        Thread t = new Thread(task, "search-lab-verifier");
        t.setDaemon(true);
        t.start();
    }

    private void unbind() {
        progress.progressProperty().unbind();
        status.textProperty().unbind();
        runBtn.setDisable(false);
    }

    private void show(List<HeuristicReport> reports, Goal goal) {
        table.setItems(FXCollections.observableArrayList(reports));
        ExactDistanceTable exact = ExactDistanceTable.forGoal(goal);
        List<XYChart.Series<Number, Number>> series = new ArrayList<>();
        XYChart.Series<Number, Number> ideal = new XYChart.Series<>();
        ideal.setName("h = h* (lý tưởng)");
        for (int d = 0; d <= exact.maxDistance(); d++) ideal.getData().add(new XYChart.Data<>(d, d));
        series.add(ideal);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Đích %s: %,d trạng thái tới được, đường kính %d bước.%n%n",
                goal, exact.reachableCount(), exact.maxDistance()));
        boolean allHold = true;
        for (HeuristicReport r : reports) {
            XYChart.Series<Number, Number> s = new XYChart.Series<>();
            s.setName(r.heuristicId());
            double[] mean = r.meanHByDistance();
            for (int d = 0; d < mean.length; d++) s.getData().add(new XYChart.Data<>(d, mean[d]));
            series.add(s);
            allHold &= r.claimsHold();
            sb.append(String.format(Locale.ROOT, "%-18s %s (%d ms)%n", r.heuristicId(), r.verdict(), r.elapsedMillis()));
            if (!r.counterexample().isEmpty()) {
                sb.append(String.format(Locale.ROOT, "    phản ví dụ [%s]: h = %d > h* = %d%n",
                        r.counterexample(), r.counterexampleH(), r.counterexampleHStar()));
            }
        }
        sb.append(allHold ? "\nMọi khai báo tính chất đều được xác minh." : "\nCÓ HEURISTIC KHAI BÁO SAI TÍNH CHẤT!");
        chart.getData().setAll(series);
        details.setText(sb.toString());
        status.setText("Hoàn tất " + reports.size() + " heuristic");
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%.2f%%", v * 100);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
