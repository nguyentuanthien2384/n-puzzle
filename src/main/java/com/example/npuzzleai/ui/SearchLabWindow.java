package com.example.npuzzleai.ui;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.InputStream;
import java.net.URL;
import java.util.function.Supplier;

/**
 * Cửa sổ "Search Lab" - biến ứng dụng thành phòng thí nghiệm heuristic search:
 * Giải &amp; Replay, Compare Lab, Kiểm định heuristic, Experiment Manager.
 * Mọi tab gọi cùng search engine với CLI và benchmark.
 */
public final class SearchLabWindow {
    private static SearchLabWindow instance;

    private final Stage stage = new Stage();
    private final SolveTab solveTab;

    private SearchLabWindow(Window owner, Supplier<Board> mainBoard, Supplier<Goal> mainGoal) {
        solveTab = new SolveTab(mainBoard, mainGoal);
        CompareTab compareTab = new CompareTab(solveTab::board, solveTab::goal);
        VerifierTab verifierTab = new VerifierTab();
        ExperimentTab experimentTab = new ExperimentTab();

        TabPane tabs = new TabPane(
                tab("Giải & Replay", solveTab.node()),
                tab("So sánh (Compare Lab)", compareTab.node()),
                tab("Kiểm định heuristic", verifierTab.node()),
                tab("Thí nghiệm (Experiment)", experimentTab.node()));
        Scene scene = new Scene(tabs, 1320, 860);
        URL css = SearchLabWindow.class.getResource("/com/example/npuzzleai/lab.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
        stage.setTitle("N-Puzzle Search Lab");
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        if (owner != null) stage.initOwner(owner);
        try (InputStream icon = SearchLabWindow.class.getResourceAsStream("/com/example/npuzzleai/img/logo.png")) {
            if (icon != null) stage.getIcons().add(new Image(icon));
        } catch (java.io.IOException ignored) {
            // Không có icon cũng không sao.
        }
    }

    private static Tab tab(String title, javafx.scene.Node content) {
        Tab t = new Tab(title, content);
        t.setClosable(false);
        return t;
    }

    /**
     * Mở (hoặc đưa lên trước) Search Lab và nạp bảng hiện tại của màn hình chính.
     *
     * @param mainBoard trả về bảng đang hiển thị ở màn hình chính
     * @param mainGoal  trả về đích đang chọn ở màn hình chính
     */
    public static void show(Window owner, Supplier<Board> mainBoard, Supplier<Goal> mainGoal) {
        if (instance == null) instance = new SearchLabWindow(owner, mainBoard, mainGoal);
        instance.solveTab.loadBoard(mainBoard.get(), mainGoal.get());
        instance.stage.show();
        instance.stage.toFront();
    }
}
