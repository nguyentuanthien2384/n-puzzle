package com.example.npuzzleai.ui;

import com.example.npuzzleai.algorithms.AlgorithmRegistry;
import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.BoardGenerator;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Move;
import com.example.npuzzleai.core.PuzzleProblem;
import com.example.npuzzleai.heuristics.BasicHeuristics;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.HeuristicProperties;
import com.example.npuzzleai.search.SearchAlgorithm;
import com.example.npuzzleai.search.SearchBudget;
import com.example.npuzzleai.search.SearchEvent;
import com.example.npuzzleai.search.SearchMetrics;
import com.example.npuzzleai.search.SearchResult;
import com.example.npuzzleai.search.TraceMode;
import com.example.npuzzleai.verify.ExactDistanceTable;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Tab "Giải &amp; Replay": chạy một thuật toán trên luồng nền (Task), theo dõi tiến độ trực tiếp,
 * phát lại lời giải hoặc quá trình mở rộng node, giải thích giá trị h và heatmap ô trống.
 */
final class SolveTab {
    private static final int MAX_EVENTS_FULL = 200_000;
    private static final int MAX_EVENTS_SAMPLED = 50_000;

    private final BorderPane root = new BorderPane();
    private final Supplier<Board> mainBoard;
    private final Supplier<Goal> mainGoal;

    private final TextField boardField = new TextField();
    private final ComboBox<Integer> sizeBox = new ComboBox<>(FXCollections.observableArrayList(3, 4, 5));
    private final ComboBox<String> goalBox = new ComboBox<>(FXCollections.observableArrayList(UiSupport.GOAL_NAMES));
    private final Spinner<Integer> walkSteps = UiSupport.intSpinner(1, 1000, 30, 5);
    private final ComboBox<String> algoBox = new ComboBox<>(FXCollections.observableArrayList(AlgorithmRegistry.defaults().baseIds()));
    private final Spinner<Double> weight = UiSupport.doubleSpinner(1.0, 20.0, 1.5, 0.25);
    private final Spinner<Integer> smaNodes = UiSupport.intSpinner(16, 50_000_000, 100_000, 10_000);
    private final Spinner<Integer> ttMb = UiSupport.intSpinner(1, 4096, 64, 16);
    private final HBox weightRow = row("Trọng số w", weight);
    private final HBox smaRow = row("Số node tối đa", smaNodes);
    private final HBox ttRow = row("Bảng TT (MB)", ttMb);
    private final ComboBox<String> heuristicBox = new ComboBox<>(FXCollections.observableArrayList(HeuristicRegistry.defaults().defaultIds()));
    private final Label algoInfo = UiSupport.hint("");
    private final Label heuristicInfo = UiSupport.hint("");
    private final Spinner<Integer> timeout = UiSupport.intSpinner(1, 3600, 60, 5);
    private final Spinner<Integer> memoryMb = UiSupport.intSpinner(0, 65_536, 1024, 128);
    private final ComboBox<TraceMode> traceBox = new ComboBox<>(FXCollections.observableArrayList(TraceMode.values()));
    private final Spinner<Integer> sampleEvery = UiSupport.intSpinner(1, 1_000_000, 16, 8);
    private final Button solveBtn = new Button("Giải");
    private final Button cancelBtn = new Button("Dừng");
    private final ProgressIndicator busy = new ProgressIndicator();
    private final Label liveLabel = new Label();

    private final BoardView boardView = new BoardView(420);
    private final ToggleGroup replayGroup = new ToggleGroup();
    private final ToggleButton solutionMode = new ToggleButton("Lời giải");
    private final ToggleButton searchMode = new ToggleButton("Quá trình tìm kiếm");
    private final Button firstBtn = new Button("⏮");
    private final Button prevBtn = new Button("◀");
    private final Button playBtn = new Button("⏯ Phát");
    private final Button nextBtn = new Button("▶");
    private final Button lastBtn = new Button("⏭");
    private final Slider speed = new Slider(1, 60, 6);
    private final Slider timeline = new Slider(0, 0, 0);
    private final Label stepLabel = new Label("Chưa có kết quả");
    private final Label nodeLabel = new Label();
    private final Label explainLabel = UiSupport.hint("");

    private final CheckBox teaching = new CheckBox("Teaching: màu theo Manhattan, viền đỏ = xung đột tuyến tính");
    private final BoardView heatView = new BoardView(240);
    private final TextArea metricsArea = new TextArea();

    private Goal goal = Goal.standard(3);
    private Board board = goal.board();
    private SearchResult lastResult;
    private Heuristic lastHeuristic;
    private List<Board> solutionFrames = List.of();
    private List<SearchEvent> searchEvents = List.of();
    private TraceRecorder recorder;
    private volatile boolean cancelRequested;
    private boolean updatingTimeline;
    private boolean loading;
    private final Timeline player = new Timeline(new KeyFrame(Duration.seconds(1), e -> stepForward()));
    private final Timeline liveTimer = new Timeline(new KeyFrame(Duration.millis(150), e -> refreshLive()));

    SolveTab(Supplier<Board> mainBoard, Supplier<Goal> mainGoal) {
        this.mainBoard = mainBoard;
        this.mainGoal = mainGoal;
        player.setCycleCount(Animation.INDEFINITE);
        liveTimer.setCycleCount(Animation.INDEFINITE);
        buildLayout();
        wireEvents();
        algoBox.setValue("ida");
        heuristicBox.setValue("linear-conflict");
        traceBox.setValue(TraceMode.SAMPLED);
        sizeBox.setValue(3);
        goalBox.setValue(Goal.STANDARD);
        updateAlgorithmControls();
        updateHeuristicInfo();
        setBoard(BoardGenerator.randomWalk(goal, 30, new Random()));
    }

    Node node() {
        return root;
    }

    Board board() {
        return board;
    }

    Goal goal() {
        return goal;
    }

    /** Nạp bảng từ màn hình chính. */
    void loadBoard(Board b, Goal g) {
        loading = true;
        try {
            sizeBox.setValue(g.size());
            goalBox.setValue(g.name().equals(Goal.CUSTOM) ? Goal.STANDARD : g.name());
        } finally {
            loading = false;
        }
        goal = g;
        setBoard(b);
        updateHeuristicInfo();
    }

    private void buildLayout() {
        root.setPadding(new Insets(10));

        VBox left = new VBox(8);
        left.setPadding(new Insets(4, 12, 4, 4));
        left.setPrefWidth(330);
        boardField.setPromptText("Ví dụ: 1 2 3 4 5 6 0 7 8");
        Button applyBtn = new Button("Áp dụng");
        applyBtn.setOnAction(e -> applyBoardText());
        Button randomBtn = new Button("Trộn ngẫu nhiên");
        randomBtn.setOnAction(e -> setBoard(BoardGenerator.randomWalk(goal, walkSteps.getValue(), new Random())));
        Button uniformBtn = new Button("Ngẫu nhiên đều");
        UiSupport.tooltip(uniformBtn, "Hoán vị ngẫu nhiên đều trên lớp khả giải - thường rất khó với 4x4");
        uniformBtn.setOnAction(e -> setBoard(BoardGenerator.uniformSolvable(goal, new Random())));
        Button fromMainBtn = new Button("Lấy từ màn chính");
        fromMainBtn.setOnAction(e -> loadBoard(mainBoard.get(), mainGoal.get()));
        for (Spinner<?> s : List.of(weight, smaNodes, ttMb, timeout, memoryMb, sampleEvery, walkSteps)) s.setPrefWidth(120);

        GridPane boardGrid = new GridPane();
        boardGrid.setHgap(8);
        boardGrid.setVgap(6);
        boardGrid.addRow(0, new Label("Kích thước"), sizeBox);
        boardGrid.addRow(1, new Label("Đích"), goalBox);
        boardGrid.addRow(2, new Label("Số bước trộn"), walkSteps);

        HBox boardInput = new HBox(6, boardField, applyBtn);
        HBox.setHgrow(boardField, Priority.ALWAYS);
        left.getChildren().addAll(
                UiSupport.title("Bảng"), boardInput, boardGrid,
                new HBox(6, randomBtn, uniformBtn), fromMainBtn,
                new Separator(),
                UiSupport.title("Thuật toán"), algoBox, weightRow, smaRow, ttRow, algoInfo,
                UiSupport.title("Heuristic"), heuristicBox, heuristicInfo,
                new Separator(),
                UiSupport.title("Ngân sách & trace"),
                row("Timeout (giây)", timeout), row("Bộ nhớ (MB, 0 = ∞)", memoryMb),
                row("Trace", traceBox), row("Lấy mẫu mỗi", sampleEvery),
                UiSupport.hint("OFF: không ghi sự kiện (đo chuẩn). SAMPLED: 1/K node. FULL_TRACE: mọi node - chỉ dùng cho 3x3."),
                new HBox(8, solveBtn, cancelBtn, busy), liveLabel);
        solveBtn.setDefaultButton(true);
        solveBtn.setPrefWidth(90);
        cancelBtn.setDisable(true);
        busy.setVisible(false);
        busy.setPrefSize(26, 26);
        liveLabel.setWrapText(true);
        ScrollPane leftScroll = new ScrollPane(left);
        leftScroll.setFitToWidth(true);
        leftScroll.setPrefWidth(350);
        root.setLeft(leftScroll);

        solutionMode.setToggleGroup(replayGroup);
        searchMode.setToggleGroup(replayGroup);
        solutionMode.setSelected(true);
        HBox modeBar = new HBox(6, new Label("Phát lại:"), solutionMode, searchMode);
        modeBar.setAlignment(Pos.CENTER_LEFT);
        speed.setPrefWidth(140);
        HBox controls = new HBox(6, firstBtn, prevBtn, playBtn, nextBtn, lastBtn, new Label("Tốc độ"), speed);
        controls.setAlignment(Pos.CENTER_LEFT);
        timeline.setPrefWidth(420);
        nodeLabel.getStyleClass().add("mono");
        VBox center = new VBox(8, boardView, modeBar, controls, timeline, stepLabel, nodeLabel, explainLabel);
        center.setPadding(new Insets(0, 12, 0, 12));
        center.setMaxWidth(460);
        root.setCenter(center);

        metricsArea.setEditable(false);
        metricsArea.setPrefRowCount(22);
        metricsArea.getStyleClass().add("mono");
        VBox right = new VBox(8, teaching, UiSupport.title("Heatmap vị trí ô trống (node đã mở rộng)"), heatView,
                UiSupport.hint("Ô càng đỏ: ô trống ở đó càng thường xuyên trong các node được mở rộng (theo mẫu trace)."),
                UiSupport.title("Số liệu"), metricsArea);
        right.setPrefWidth(340);
        VBox.setVgrow(metricsArea, Priority.ALWAYS);
        root.setRight(right);
        setReplayEnabled(false);
    }

    private static HBox row(String label, Node control) {
        Label l = new Label(label);
        l.setMinWidth(130);
        HBox box = new HBox(6, l, control);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private void wireEvents() {
        boardField.setOnAction(e -> applyBoardText());
        sizeBox.setOnAction(e -> {
            Integer size = sizeBox.getValue();
            if (loading || size == null || (board != null && board.size() == size)) return;
            goal = UiSupport.goal(goalBox.getValue() == null ? Goal.STANDARD : goalBox.getValue(), size);
            setBoard(BoardGenerator.randomWalk(goal, walkSteps.getValue(), new Random()));
            updateHeuristicInfo();
        });
        goalBox.setOnAction(e -> {
            String name = goalBox.getValue();
            if (loading || name == null || board == null || name.equals(goal.name())) return;
            goal = UiSupport.goal(name, board.size());
            setBoard(board);
        });
        algoBox.setOnAction(e -> updateAlgorithmControls());
        heuristicBox.setOnAction(e -> updateHeuristicInfo());
        solveBtn.setOnAction(e -> startSolve());
        cancelBtn.setOnAction(e -> cancelRequested = true);
        teaching.setOnAction(e -> boardView.setOverlay(teaching.isSelected() ? BoardView.Overlay.TEACHING : BoardView.Overlay.NONE));
        heatView.setOverlay(BoardView.Overlay.HEATMAP);
        replayGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) {
                (o == null ? solutionMode : (ToggleButton) o).setSelected(true);
                return;
            }
            resetTimeline();
        });
        firstBtn.setOnAction(e -> showFrame(0));
        prevBtn.setOnAction(e -> showFrame(currentFrame() - 1));
        nextBtn.setOnAction(e -> showFrame(currentFrame() + 1));
        lastBtn.setOnAction(e -> showFrame(frameCount() - 1));
        playBtn.setOnAction(e -> togglePlay());
        speed.valueProperty().addListener((obs, o, n) -> player.setRate(n.doubleValue()));
        player.setRate(speed.getValue());
        timeline.valueProperty().addListener((obs, o, n) -> {
            if (!updatingTimeline) showFrame((int) Math.round(n.doubleValue()));
        });
    }

    private void applyBoardText() {
        try {
            Board b = Board.parse(boardField.getText());
            if (b.size() != goal.size()) goal = UiSupport.goal(goalBox.getValue(), b.size());
            sizeBox.setValue(b.size());
            setBoard(b);
        } catch (IllegalArgumentException ex) {
            UiSupport.error("Trạng thái không hợp lệ", ex);
        }
    }

    private void setBoard(Board b) {
        board = b;
        boardField.setText(b.toString());
        boardView.show(b, goal);
        heatView.show(b, goal);
        heatView.setHeat(null);
        lastResult = null;
        solutionFrames = List.of();
        searchEvents = List.of();
        player.stop();
        setReplayEnabled(false);
        stepLabel.setText("Chưa có kết quả");
        nodeLabel.setText("");
        explain(b);
    }

    private void updateAlgorithmControls() {
        String base = algoBox.getValue();
        if (base == null) return;
        weightRow.setVisible(base.equals("wastar"));
        weightRow.setManaged(base.equals("wastar"));
        smaRow.setVisible(base.equals("sma"));
        smaRow.setManaged(base.equals("sma"));
        ttRow.setVisible(base.equals("ida-tt"));
        ttRow.setManaged(base.equals("ida-tt"));
        SearchAlgorithm algo = AlgorithmRegistry.defaults().create(currentSpec());
        heuristicBox.setDisable(!algo.properties().usesHeuristic());
        algoInfo.setText(algo.description() + (algo.properties().optimalWithAdmissibleHeuristic()
                ? "  [tối ưu với h chấp nhận được]" : "  [không đảm bảo tối ưu]"));
    }

    private void updateHeuristicInfo() {
        String id = heuristicBox.getValue();
        if (id == null) return;
        Heuristic h = HeuristicRegistry.defaults().create(id);
        HeuristicProperties p = h.properties();
        String support = h.supports(goal.size()) ? "" : "  ⚠ không hỗ trợ bảng " + goal.size() + "x" + goal.size();
        heuristicInfo.setText("[" + p.flags() + "] " + p.note() + support);
    }

    private String currentSpec() {
        return UiSupport.algorithmSpec(algoBox.getValue(), weight.getValue(), smaNodes.getValue(), ttMb.getValue());
    }

    private void startSolve() {
        SearchAlgorithm algo;
        try {
            algo = AlgorithmRegistry.defaults().create(currentSpec());
        } catch (IllegalArgumentException ex) {
            UiSupport.error("Cấu hình thuật toán không hợp lệ", ex);
            return;
        }
        Heuristic heuristic = HeuristicRegistry.defaults().create(heuristicBox.getValue());
        SearchBudget budget = new SearchBudget(timeout.getValue() * 1000L, 0, memoryMb.getValue() * 1024L * 1024L);
        Board start = board;
        Goal g = goal;
        TraceMode mode = traceBox.getValue();
        cancelRequested = false;
        recorder = new TraceRecorder(start.cellCount(), mode, sampleEvery.getValue(),
                mode == TraceMode.FULL_TRACE ? MAX_EVENTS_FULL : MAX_EVENTS_SAMPLED, () -> cancelRequested);
        TraceRecorder rec = recorder;
        lastHeuristic = algo.properties().usesHeuristic() ? heuristic : null;

        Task<SearchResult> task = new Task<>() {
            @Override
            protected SearchResult call() {
                if (algo.properties().usesHeuristic() && heuristic.supports(start.size())) {
                    updateMessage("Tiền xử lý heuristic (dựng/nạp bảng)...");
                    heuristic.prepare(g);
                }
                updateMessage("Đang tìm kiếm...");
                return algo.solve(new PuzzleProblem(start, g), heuristic, budget, rec);
            }
        };
        liveLabel.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> finish(task.getValue()));
        task.setOnFailed(e -> {
            finishUi();
            UiSupport.error("Lỗi khi tìm kiếm", task.getException());
        });
        setRunning(true);
        liveTimer.play();
        Thread t = new Thread(task, "search-lab-solver");
        t.setDaemon(true);
        t.start();
    }

    private void refreshLive() {
        TraceRecorder rec = recorder;
        if (rec == null) return;
        SearchMetrics m = rec.latestMetrics();
        SearchEvent e = rec.latestEvent();
        StringBuilder sb = new StringBuilder();
        if (m != null) {
            sb.append("Đã mở rộng ").append(UiSupport.fmt(m.expanded)).append(" • sinh ").append(UiSupport.fmt(m.generated))
                    .append(" • max OPEN ").append(UiSupport.fmt(m.maxOpen));
        }
        if (e != null) {
            sb.append("\nĐang xét: g=").append(e.g()).append(" h=").append(e.h())
                    .append(" f=").append(UiSupport.fmt(e.f())).append(" độ sâu=").append(e.depth());
            boardView.show(e.board(), goal);
            heatView.setHeat(rec.blankHeat());
        }
        if (sb.length() > 0) {
            liveLabel.textProperty().unbind();
            liveLabel.setText(sb.toString());
        }
    }

    private void finish(SearchResult r) {
        finishUi();
        lastResult = r;
        solutionFrames = r.solved() ? r.path() : List.of();
        searchEvents = recorder == null ? List.of() : List.copyOf(recorder.events());
        heatView.setHeat(recorder == null ? null : recorder.blankHeat());
        metricsArea.setText(describe(r));
        liveLabel.setText(r.status().label() + " - " + r.message());
        boolean any = !solutionFrames.isEmpty() || !searchEvents.isEmpty();
        setReplayEnabled(any);
        if (!any) boardView.show(board, goal);
        if (solutionFrames.isEmpty() && !searchEvents.isEmpty()) searchMode.setSelected(true);
        else solutionMode.setSelected(true);
        resetTimeline();
    }

    private void finishUi() {
        liveTimer.stop();
        liveLabel.textProperty().unbind();
        setRunning(false);
        updateAlgorithmControls();
    }

    private void setRunning(boolean running) {
        solveBtn.setDisable(running);
        cancelBtn.setDisable(!running);
        busy.setVisible(running);
        algoBox.setDisable(running);
        heuristicBox.setDisable(running);
        sizeBox.setDisable(running);
        goalBox.setDisable(running);
        if (running) {
            player.stop();
            setReplayEnabled(false);
        }
    }

    private String describe(SearchResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Trạng thái : ").append(r.status()).append('\n');
        sb.append("Thông báo  : ").append(r.message()).append('\n');
        sb.append("Thuật toán : ").append(r.algorithmId()).append('\n');
        sb.append("Heuristic  : ").append(r.heuristicId()).append('\n');
        if (r.solved()) {
            sb.append("Số bước    : ").append(r.length()).append('\n');
            if (r.start().size() <= 3) {
                int exact = ExactDistanceTable.forGoal(r.goal()).distance(r.start());
                sb.append("Tối ưu (h*): ").append(exact).append(exact == r.length() ? "  ✓ tối ưu" : "  ✗ dài hơn tối ưu").append('\n');
            }
            String moves = Move.format(r.moves());
            sb.append("Nước đi    : ").append(moves.length() > 120 ? moves.substring(0, 120) + "…" : moves).append('\n');
        }
        sb.append('\n');
        for (Map.Entry<String, Long> e : r.metrics().toMap().entrySet()) {
            long v = e.getValue();
            if (v < 0) continue;
            String value = e.getKey().endsWith("Ns") ? UiSupport.millis(v) : UiSupport.fmt(v);
            sb.append(String.format("%-18s %s%n", e.getKey(), value));
        }
        if (recorder != null && recorder.truncated()) sb.append("\n(Trace bị cắt bớt - tăng 'Lấy mẫu mỗi')");
        return sb.toString();
    }

    private boolean isSolutionMode() {
        return replayGroup.getSelectedToggle() == solutionMode;
    }

    private int frameCount() {
        return isSolutionMode() ? solutionFrames.size() : searchEvents.size();
    }

    private int currentFrame() {
        return (int) Math.round(timeline.getValue());
    }

    private void resetTimeline() {
        player.stop();
        int count = frameCount();
        updatingTimeline = true;
        timeline.setMin(0);
        timeline.setMax(Math.max(0, count - 1));
        timeline.setValue(0);
        timeline.setMajorTickUnit(Math.max(1, count / 10.0));
        updatingTimeline = false;
        if (count > 0) showFrame(0);
        else stepLabel.setText(isSolutionMode() ? "Không có lời giải để phát lại" : "Không có trace (chọn SAMPLED hoặc FULL_TRACE)");
    }

    private void showFrame(int index) {
        int count = frameCount();
        if (count == 0) return;
        int i = Math.max(0, Math.min(count - 1, index));
        updatingTimeline = true;
        timeline.setValue(i);
        updatingTimeline = false;
        Board b;
        if (isSolutionMode()) {
            b = solutionFrames.get(i);
            int h = lastHeuristic == null ? 0 : lastHeuristic.estimate(b, goal);
            stepLabel.setText("Bước " + i + " / " + (count - 1));
            nodeLabel.setText("g = " + i + "   h = " + h + "   f = " + (i + h) + "   còn lại thực tế = " + (count - 1 - i));
        } else {
            SearchEvent e = searchEvents.get(i);
            b = e.board();
            stepLabel.setText("Sự kiện " + (i + 1) + " / " + count + "  (node mở rộng thứ " + UiSupport.fmt(e.expansionIndex()) + ")");
            nodeLabel.setText("g = " + e.g() + "   h = " + e.h() + "   f = " + UiSupport.fmt(e.f()) + "   độ sâu = " + e.depth());
        }
        boardView.show(b, goal);
        explain(b);
    }

    /** Giải thích thành phần của h tại bảng đang hiển thị. */
    private void explain(Board b) {
        if (b == null || b.size() != goal.size()) return;
        int md = BasicHeuristics.manhattan(b, goal);
        int lc = BasicHeuristics.linearConflictPenalty(b, goal);
        StringBuilder sb = new StringBuilder("Manhattan = ").append(md).append("   Xung đột tuyến tính = +").append(lc)
                .append("   → MD+LC = ").append(md + lc);
        String id = heuristicBox.getValue();
        if (id != null) {
            Heuristic h = HeuristicRegistry.defaults().create(id);
            if (h.supports(b.size()) && (!h.properties().requiresPreprocessing() || lastHeuristic == h)) {
                sb.append("   |  h(").append(id).append(") = ").append(h.estimate(b, goal));
            }
        }
        if (b.size() <= 3) sb.append("   |  h* = ").append(ExactDistanceTable.forGoal(goal).distance(b));
        explainLabel.setText(sb.toString());
    }

    private void togglePlay() {
        if (player.getStatus() == Animation.Status.RUNNING) {
            player.stop();
            playBtn.setText("⏯ Phát");
        } else {
            if (currentFrame() >= frameCount() - 1) showFrame(0);
            player.play();
            playBtn.setText("⏸ Dừng");
        }
    }

    private void stepForward() {
        if (currentFrame() >= frameCount() - 1) {
            player.stop();
            playBtn.setText("⏯ Phát");
            return;
        }
        showFrame(currentFrame() + 1);
    }

    private void setReplayEnabled(boolean enabled) {
        for (Node n : List.of(firstBtn, prevBtn, playBtn, nextBtn, lastBtn, timeline, solutionMode, searchMode)) {
            n.setDisable(!enabled);
        }
        if (!enabled) playBtn.setText("⏯ Phát");
    }
}
