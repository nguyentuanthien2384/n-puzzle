package com.example.npuzzleai.ui;

import com.example.npuzzleai.core.Goal;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Tiện ích dựng giao diện dùng chung cho các tab của Search Lab. */
final class UiSupport {
    static final List<String> GOAL_NAMES = List.of(Goal.STANDARD, Goal.BLANK_FIRST);

    private UiSupport() {
    }

    static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }

    static Label hint(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("hint");
        l.setWrapText(true);
        return l;
    }

    static Spinner<Integer> intSpinner(int min, int max, int initial, int step) {
        Spinner<Integer> s = new Spinner<>(min, max, initial, step);
        s.setEditable(true);
        s.setPrefWidth(110);
        return s;
    }

    static Spinner<Double> doubleSpinner(double min, double max, double initial, double step) {
        Spinner<Double> s = new Spinner<>(min, max, initial, step);
        s.setEditable(true);
        s.setPrefWidth(110);
        return s;
    }

    static String fmt(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    static String fmt(double v) {
        if (Double.isNaN(v)) return "-";
        return String.format(Locale.ROOT, "%,.2f", v);
    }

    static String millis(long nanos) {
        return String.format(Locale.ROOT, "%,.2f ms", nanos / 1e6);
    }

    static Goal goal(String name, int size) {
        return Goal.parse(name, size);
    }

    /** Tham số nhập trên giao diện cho các thuật toán có tham số. */
    record AlgorithmParams(double weight, int smaNodes, int ttMegabytes, int workers, int simulations) {
    }

    /** Ghép mã thuật toán với tham số từ các ô nhập. */
    static String algorithmSpec(String base, AlgorithmParams p) {
        return switch (base) {
            case "wastar" -> "wastar:" + p.weight();
            case "focal" -> "focal:" + p.weight();
            case "sma" -> "sma:" + p.smaNodes();
            case "ida-tt" -> "ida-tt:" + p.ttMegabytes();
            case "hda" -> "hda:" + p.workers();
            case "mcts" -> "mcts:" + p.simulations();
            default -> base;
        };
    }

    static FlowPane checkBoxes(List<String> ids, Set<String> selected, List<CheckBox> sink) {
        FlowPane pane = new FlowPane(8, 6);
        pane.setPadding(new Insets(2, 0, 2, 0));
        for (String id : ids) {
            CheckBox cb = new CheckBox(id);
            cb.setSelected(selected.contains(id));
            sink.add(cb);
            pane.getChildren().add(cb);
        }
        return pane;
    }

    static List<String> selected(List<CheckBox> boxes) {
        List<String> ids = new ArrayList<>();
        for (CheckBox cb : boxes) if (cb.isSelected()) ids.add(cb.getText());
        return ids;
    }

    static <T, V> TableColumn<T, V> column(String title, Function<T, V> getter, double width) {
        TableColumn<T, V> col = new TableColumn<>(title);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(getter.apply(c.getValue())));
        col.setPrefWidth(width);
        return col;
    }

    static void tooltip(javafx.scene.control.Control control, String text) {
        control.setTooltip(new Tooltip(text));
    }

    static void error(String header, Throwable e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Search Lab");
        alert.setHeaderText(header);
        alert.setContentText(e == null ? "" : String.valueOf(e.getMessage()));
        alert.show();
    }
}
