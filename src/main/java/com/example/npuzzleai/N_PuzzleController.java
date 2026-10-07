package com.example.npuzzleai;

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
import com.example.npuzzleai.ui.SearchLabWindow;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.canvas.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;

import java.io.File;
import java.net.URL;
import java.util.*;

import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

public class N_PuzzleController implements Initializable, Runnable {
    @FXML
    private ToggleGroup difficultyToggle;
    @FXML
    private ToggleGroup algorithmToggle;
    @FXML
    private ToggleGroup goalToggle;
    @FXML
    private Canvas imgCanvas;
    @FXML
    private ImageView imgView;
    @FXML
    private ImageView goal1Image;
    @FXML
    private ImageView goal2Image;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Button solveBtn;
    @FXML
    private Button playBtn;
    @FXML
    private Button jumbleBtn;
    @FXML
    private Button addImage;
    @FXML
    private Button addNumber;
    @FXML
    private Button compareBtn;
    @FXML
    private Button labBtn;
    @FXML
    private SplitMenuButton sizeMenu;
    @FXML
    private SplitMenuButton algorithmMenu;
    @FXML
    private RadioButton goal1;
    @FXML
    private RadioButton goal2;
    @FXML
    private TextField stepField;
    @FXML
    private AnchorPane displayPane;

    public AStar aStar;
    public BFS bFS;
    public Image image;
    public HandleImage handledImage;
    private int size;
    private State state;
    private State goalState;
    private int[] value;
    private Vector<int[]> result;
    private String algorithm;
    private String algorithmBeforeCompare;
    private int countStep = 0;
    private boolean isSolve = false;
    private boolean isPlay = false;
    private int approvedNodes;
    private int totalNodes;
    private long solveTime;
    private long startTime;
    private String error;
    private final Vector<Result> compareResults = new Vector<>();
    /** Cờ hủy cho các thuật toán chạy qua search engine mới (RBFS, SMA*, W-A*, IDA*-TT). */
    private volatile boolean engineCancelled = false;

    @Override
    // Trạng thái khởi tạo ban đầu
    public void initialize(URL url, ResourceBundle resourceBundle) {
        State.heuristic = 1;
        State.goal = 1;
        size = 3;
        algorithm = "A*";
        state = new State(size);
        value = state.createGoalArray();
        goalState = new State(size);
        goalState.createGoalArray();
        displayImage(null);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressBar.setVisible(false);
        goal1Image.setImage(new Image(Objects.requireNonNull(N_PuzzleApplication.class.getResourceAsStream("img/goal-1.png"))));
        goal2Image.setImage(new Image(Objects.requireNonNull(N_PuzzleApplication.class.getResourceAsStream("img/goal-2.png"))));
    }
    // Luồng chạy lời giải
    public void run() {
        if (result == null || result.isEmpty()) {
            Platform.runLater(this::notSolve);
            return;
        }
        int totalStep = result.size() - 1;
        // Tốc độ phát lại thích ứng: lời giải dài (tìm kiếm cục bộ) phải phát nhanh hơn.
        long delay = totalStep <= 60 ? 600 : totalStep <= 300 ? 150 : totalStep <= 2000 ? 40 : 15;
        for (int i = 0; i <= totalStep; i++) {
            if (Thread.currentThread().isInterrupted()) break;
            int[] frame = result.get(i).clone();
            int currentStep = i;
            Platform.runLater(() -> {
                value = frame;
                state.value = frame;
                stepField.setText(currentStep + "/" + totalStep);
                displayImage(image);
            });
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Platform.runLater(this::notSolve);
    }

    @FXML
    // Chọn size bảng
    public void onChangeImageSize() {
        RadioMenuItem selectedDiff = (RadioMenuItem) difficultyToggle.getSelectedToggle();
        switch (selectedDiff.getId()) {
            case "medium" -> size = 4;
            case "hard" -> size = 5;
            default -> size = 3;
        }
        sizeMenu.setText(selectedDiff.getText());
        state = new State(size);
        value = state.createGoalArray();
        goalState = new State(size);
        goalState.createGoalArray();
        countStep = 0;
        stepField.setText("0");
        displayImage(image);
    }
    // Chọn thuật toán
    public void onChangeAlgorithm() {
        RadioMenuItem selectedAlgorithm = (RadioMenuItem) algorithmToggle.getSelectedToggle();
        switch (selectedAlgorithm.getId()) {
            case "heuristic1" -> {
                State.heuristic = 1;
                algorithm = "A*";
            }
            case "heuristic2" -> {
                State.heuristic = 2;
                algorithm = "A*";
            }
            case "heuristic3" -> {
                State.heuristic = 3;
                algorithm = "A*";
            }
            case "heuristic4" -> {
                State.heuristic = 4;
                algorithm = "A*";
            }
            case "heuristic5" -> {
                State.heuristic = 5;
                algorithm = "A*";
            }
            case "heuristic6" -> {
                State.heuristic = 6;
                algorithm = "A*";
            }
            case "heuristic7" -> {
                State.heuristic = 7;
                algorithm = "A*";
            }
            case "heuristic8" -> {
                State.heuristic = 8;
                algorithm = "A*";
            }
            case "heuristic9" -> {
                State.heuristic = 9;
                algorithm = "A*";
            }
            // IDA* và Greedy dùng heuristic đang chọn trước đó.
            case "ida" -> algorithm = "IDA*";
            // Các thuật toán của search engine mới cũng dùng heuristic H1-H9 đang chọn.
            case "idatt" -> algorithm = "IDA*-TT";
            case "rbfs" -> algorithm = "RBFS";
            case "sma" -> algorithm = "SMA*";
            case "wastar" -> algorithm = "Weighted A*";
            case "greedy" -> algorithm = "Greedy";
            case "bibfs" -> algorithm = "Bi-BFS";
            case "valueiter" -> algorithm = "Value Iteration";
            case "hill" -> algorithm = "Hill Climbing";
            case "anneal" -> algorithm = "Simulated Annealing";
            case "genetic" -> algorithm = "Genetic Algorithm";
            default -> algorithm = "BFS";
        }
        algorithmMenu.setText(selectedAlgorithm.getText());
    }
    // Thay đổi trạng thái đích
    public void onChangeGoal() {
        RadioButton selectedGoal = (RadioButton) goalToggle.getSelectedToggle();
        if (Objects.equals(selectedGoal.getId(), "goal1")) {
            State.goal = 1;
        } else {
            State.goal = 2;
        }
        value = state.createGoalArray();
        goalState.createGoalArray();
        countStep = 0;
        stepField.setText("0");
        displayImage(image);
    }
    // Button thêm ảnh
    public void onAddImgBtnClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );
        File file = fileChooser.showOpenDialog(null);
        if (file != null) {
            image = new Image(file.toURI().toString());
            // Thêm ảnh nhỏ
            if (image.getHeight() > image.getWidth()) {
                double width = image.getWidth() * 180 / image.getHeight();
                imgView.setX((180 - width) / 2);
            } else {
                imgView.setX(0);
            }
            countStep = 0;
            stepField.setText("0");
            imgView.setImage(image);
            value = state.createGoalArray();
            displayImage(image);
        }
    }
    // Button thêm bảng số
    public void onAddNumberBtnClick() {
        countStep = 0;
        image = null;
        stepField.setText("0");
        imgView.setImage(null);
        value = state.createGoalArray();
        displayImage(image);
    }
    // Button trộn ảnh
    public void onJumbleBtnClick() {
        stepField.setText("0");
        countStep = 0;
        value = state.createRandomArray();
        displayImage(image);
    }
    // Button tìm kết quả
    public void onSolveBtnClick() {
        countStep = 0;
        if (!isSolve) {
            resetStopFlags();
            solving();
            Thread thread = solveThread();
            thread.setDaemon(true);
            thread.start();
        } else {
            stopAllSolvers();
            notSolve();
        }
    }
    // Xoá cờ dừng của mọi thuật toán trước khi giải mới
    private void resetStopFlags() {
        engineCancelled = false;
        BFS.stop = false;
        AStar.stop = false;
        IDAStar.stop = false;
        GreedyBestFirst.stop = false;
        BidirectionalBFS.stop = false;
        ValueIteration.stop = false;
        HillClimbing.stop = false;
        SimulatedAnnealing.stop = false;
        GeneticAlgorithm.stop = false;
    }
    // Yêu cầu dừng mọi thuật toán đang chạy
    private void stopAllSolvers() {
        engineCancelled = true;
        BFS.stop = true;
        AStar.stop = true;
        IDAStar.stop = true;
        GreedyBestFirst.stop = true;
        BidirectionalBFS.stop = true;
        ValueIteration.stop = true;
        HillClimbing.stop = true;
        SimulatedAnnealing.stop = true;
        GeneticAlgorithm.stop = true;
    }
    // Mở cửa sổ Search Lab với bảng và đích hiện tại
    public void onOpenLabClick() {
        SearchLabWindow.show(labBtn.getScene().getWindow(),
                () -> Board.fromArray(state.value.clone()),
                () -> Goal.of(Board.fromArray(goalState.value.clone())));
    }
    // Button so sánh Heuristic
    public void onCompareBtnClick() {
        if (isSolve) return;
        algorithmBeforeCompare = algorithm;
        algorithm = "A*";
        AStar.stop = false;
        compareResults.clear();
        solving();
        Thread thread = compareThread();
        thread.setDaemon(true);
        thread.start();
    }
    // Button chơi
    public void onPlayBtnClick() {
        if (!isPlay) {
            playing();
            startTime = System.currentTimeMillis();
        } else {
            countStep = 0;
            notPlay();
        }
    }
    // Sự kiện từ bàn phím
    public void onKeyPressed(KeyEvent ke) {
        if (!isPlay) return;

        int[] before = state.value.clone();
        switch (ke.getCode()) {
            case W -> state.UP();
            case A -> state.LEFT();
            case S -> state.DOWN();
            case D -> state.RIGHT();
            default -> {
                return;
            }
        }

        value = state.value;
        if (!Arrays.equals(before, value)) {
            countStep++;
        }
        if (Arrays.equals(value, goalState.value) && countStep != 0) {
            showResult();
            countStep = 0;
        }
        stepField.setText(String.valueOf(countStep));
        displayImage(image);
    }
    // Sự kiện click chuột
    public void onMouseClicked(MouseEvent me) {
        if (!isPlay) return;

        double boardWidth = imgCanvas.getWidth();
        double boardHeight = imgCanvas.getHeight();
        double offsetX = 0;
        double offsetY = 0;
        if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
            if (image.getWidth() > image.getHeight()) {
                boardHeight = boardWidth * image.getHeight() / image.getWidth();
            } else if (image.getHeight() > image.getWidth()) {
                boardWidth = boardHeight * image.getWidth() / image.getHeight();
                offsetX = (imgCanvas.getWidth() - boardWidth) / 2.0;
            }
        }
        if (me.getX() < offsetX || me.getX() >= offsetX + boardWidth
                || me.getY() < offsetY || me.getY() >= offsetY + boardHeight) {
            return;
        }

        int blank = state.posBlank(state.value);
        int x = blank % size;
        int y = blank / size;
        int mx = (int) ((me.getX() - offsetX) / boardWidth * size);
        int my = (int) ((me.getY() - offsetY) / boardHeight * size);

        int[] before = state.value.clone();
        if (mx == x && my == y - 1) {
            state.UP();
        } else if (mx == x && my == y + 1) {
            state.DOWN();
        } else if (mx == x - 1 && my == y) {
            state.LEFT();
        } else if (mx == x + 1 && my == y) {
            state.RIGHT();
        } else {
            return;
        }

        value = state.value;
        if (!Arrays.equals(before, value)) countStep++;
        if (Arrays.equals(value, goalState.value) && countStep != 0) {
            showResult();
            countStep = 0;
        }
        stepField.setText(String.valueOf(countStep));
        displayImage(image);
    }
    // Giải quyết bài toán bằng thuật toán A*
    public void solveAStar() {
        aStar = new AStar();
        State startSnapshot = new State(state.value, size);
        State goalSnapshot = new State(goalState.value, size);
        aStar.startNode = new Node(startSnapshot, 0);
        aStar.goalNode = new Node(goalSnapshot, 1);
        aStar.solve();
        result = aStar.RESULT;
        approvedNodes = aStar.approvedNodes;
        totalNodes = aStar.totalNodes;
        solveTime = aStar.time;
        error = aStar.error;
    }
    // Giải quyết bài toán bằng thuật toán BFS
    public void solveBFS() {
        bFS = new BFS();
        State startSnapshot = new State(state.value, size);
        State goalSnapshot = new State(goalState.value, size);
        bFS.startNode = new Node(startSnapshot, 0);
        bFS.goalNode = new Node(goalSnapshot, 0);
        bFS.solve();
        result = bFS.RESULT;
        approvedNodes = bFS.approvedNodes;
        totalNodes = bFS.totalNodes;
        solveTime = bFS.time;
        error = bFS.error;
    }
    // Giải bằng IDA* - bộ nhớ thấp, phù hợp 4x4/5x5
    public void solveIDAStar() {
        IDAStar solver = new IDAStar();
        solver.startNode = new Node(new State(state.value, size), 0);
        solver.goalNode = new Node(new State(goalState.value, size), 0);
        solver.solve();
        result = solver.RESULT;
        approvedNodes = solver.approvedNodes;
        totalNodes = solver.totalNodes;
        solveTime = solver.time;
        error = solver.error;
    }
    // Giải bằng Greedy Best-First - nhanh nhưng không tối ưu
    public void solveGreedy() {
        GreedyBestFirst solver = new GreedyBestFirst();
        solver.startNode = new Node(new State(state.value, size), 0);
        solver.goalNode = new Node(new State(goalState.value, size), 0);
        solver.solve();
        result = solver.RESULT;
        approvedNodes = solver.approvedNodes;
        totalNodes = solver.totalNodes;
        solveTime = solver.time;
        error = solver.error;
    }
    // Giải bằng tìm kiếm hai chiều
    public void solveBidirectional() {
        BidirectionalBFS solver = new BidirectionalBFS();
        solver.startNode = new Node(new State(state.value, size), 0);
        solver.goalNode = new Node(new State(goalState.value, size), 0);
        solver.solve();
        result = solver.RESULT;
        approvedNodes = solver.approvedNodes;
        totalNodes = solver.totalNodes;
        solveTime = solver.time;
        error = solver.error;
    }
    // Giải bằng lặp giá trị trên MDP (3x3)
    public void solveValueIteration() {
        ValueIteration solver = new ValueIteration();
        solver.startNode = new Node(new State(state.value, size), 0);
        solver.goalNode = new Node(new State(goalState.value, size), 0);
        solver.solve();
        result = solver.RESULT;
        approvedNodes = solver.approvedNodes;
        totalNodes = solver.totalNodes;
        solveTime = solver.time;
        error = solver.error;
    }
    // Giải bằng leo đồi (3x3)
    public void solveHillClimbing() {
        HillClimbing solver = new HillClimbing();
        solver.startNode = new Node(new State(state.value, size), 0);
        solver.goalNode = new Node(new State(goalState.value, size), 0);
        solver.solve();
        result = solver.RESULT;
        approvedNodes = solver.approvedNodes;
        totalNodes = solver.totalNodes;
        solveTime = solver.time;
        error = solver.error;
    }
    // Giải bằng mô phỏng ủ (3x3)
    public void solveSimulatedAnnealing() {
        SimulatedAnnealing solver = new SimulatedAnnealing();
        solver.startNode = new Node(new State(state.value, size), 0);
        solver.goalNode = new Node(new State(goalState.value, size), 0);
        solver.solve();
        result = solver.RESULT;
        approvedNodes = solver.approvedNodes;
        totalNodes = solver.totalNodes;
        solveTime = solver.time;
        error = solver.error;
    }
    // Giải bằng thuật toán di truyền (3x3)
    public void solveGenetic() {
        GeneticAlgorithm solver = new GeneticAlgorithm();
        solver.startNode = new Node(new State(state.value, size), 0);
        solver.goalNode = new Node(new State(goalState.value, size), 0);
        solver.solve();
        result = solver.RESULT;
        approvedNodes = solver.approvedNodes;
        totalNodes = solver.totalNodes;
        solveTime = solver.time;
        error = solver.error;
    }
    /**
     * Giải bằng search engine mới (cùng code path với Search Lab, CLI và benchmark),
     * dùng heuristic H1-H9 đang chọn và giới hạn 60 giây như các thuật toán cũ.
     */
    public void solveWithEngine(String algorithmSpec) {
        SearchAlgorithm solver = AlgorithmRegistry.defaults().create(algorithmSpec);
        Heuristic heuristic = HeuristicRegistry.defaults().create(HeuristicRegistry.legacyId(State.heuristic));
        Board start = Board.fromArray(state.value.clone());
        Goal goal = Goal.of(Board.fromArray(goalState.value.clone()));
        if (solver.properties().usesHeuristic() && heuristic.supports(size)) heuristic.prepare(goal);
        SearchResult r = solver.solve(new PuzzleProblem(start, goal), heuristic, SearchBudget.DEFAULT,
                SearchObserver.cancellable(() -> engineCancelled));
        Vector<int[]> path = new Vector<>();
        if (r.solved()) {
            for (Board b : r.path()) path.add(b.toArray());
        }
        result = path;
        approvedNodes = (int) Math.min(Integer.MAX_VALUE, r.metrics().expanded);
        totalNodes = (int) Math.min(Integer.MAX_VALUE, r.metrics().generated);
        solveTime = r.metrics().wallTimeNs / 1_000_000L;
        error = r.solved() ? null : r.message();
    }
    // Luồng tìm kiếm lời giải
    public Thread solveThread() {
        return new Thread(() -> {
            switch (algorithm) {
                case "BFS" -> solveBFS();
                case "IDA*" -> solveIDAStar();
                case "IDA*-TT" -> solveWithEngine("ida-tt:64");
                case "RBFS" -> solveWithEngine("rbfs");
                case "SMA*" -> solveWithEngine("sma:" + 200_000);
                case "Weighted A*" -> solveWithEngine("wastar:1.5");
                case "Greedy" -> solveGreedy();
                case "Bi-BFS" -> solveBidirectional();
                case "Value Iteration" -> solveValueIteration();
                case "Hill Climbing" -> solveHillClimbing();
                case "Simulated Annealing" -> solveSimulatedAnnealing();
                case "Genetic Algorithm" -> solveGenetic();
                default -> solveAStar();
            }
            if (result != null && result.size() > 1) {
                Platform.runLater(this::showAlert);
            } else if (result != null && result.size() == 1 && error == null) {
                Platform.runLater(this::notSolve);
            } else if (error != null && !error.startsWith("Đã dừng")) {
                Platform.runLater(this::showWarning);
            } else {
                Platform.runLater(this::notSolve);
            }
        }, "npuzzle-solver");
    }
    // Luồng so sánh Heuristic
    public Thread compareThread() {
        return new Thread(() -> {
            int oldHeuristic = State.heuristic;
            Vector<int[]> bestPath = null;
            int bestApproved = Integer.MAX_VALUE;

            try {
                if (Arrays.equals(state.value, goalState.value)) {
                    Platform.runLater(this::notSolve);
                    return;
                }

                for (int i = 1; i <= 8; i++) {
                    if (AStar.stop || Thread.currentThread().isInterrupted()) break;
                    State.heuristic = i;
                    solveAStar();
                    int steps = result == null || result.isEmpty() ? 0 : result.size() - 1;
                    compareResults.add(new Result("H" + i, approvedNodes, totalNodes, steps, solveTime, error));

                    if (error == null && result != null && result.size() > 1 && approvedNodes < bestApproved) {
                        bestApproved = approvedNodes;
                        bestPath = deepCopyPath(result);
                    }
                }

                if (AStar.stop || Thread.currentThread().isInterrupted()) {
                    compareResults.clear();
                    Platform.runLater(this::notSolve);
                } else {
                    if (bestPath != null) result = bestPath;
                    Platform.runLater(this::showCompare);
                }
            } finally {
                State.heuristic = oldHeuristic;
                if (algorithmBeforeCompare != null) {
                    algorithm = algorithmBeforeCompare;
                    algorithmBeforeCompare = null;
                }
            }
        }, "npuzzle-heuristic-compare");
    }

    private Vector<int[]> deepCopyPath(Vector<int[]> source) {
        Vector<int[]> copy = new Vector<>();
        for (int[] board : source) copy.add(board.clone());
        return copy;
    }
    // Trạng thái đang tìm kiếm
    public void solving() {
        isSolve = true;
        solveBtn.setText("Dừng");
        playBtn.setDisable(true);
        setDisable();
    }
    // Trạng thái không tìm kiếm
    public void notSolve() {
        isSolve = false;
        solveBtn.setText("AI Giải");
        playBtn.setDisable(false);
        setEnable();
    }
    // Trạng thái người chơi
    public void playing() {
        isPlay = true;
        playBtn.setText("Dừng");
        solveBtn.setDisable(true);
        setDisable();
    }
    // Trạng thái không chơi
    public void notPlay() {
        isPlay = false;
        playBtn.setText("Chơi");
        solveBtn.setDisable(false);
        setEnable();
    }
    // Enable các nút
    private void setEnable() {
        solveBtn.setDisable(false);
        jumbleBtn.setDisable(false);
        addImage.setDisable(false);
        addNumber.setDisable(false);
        compareBtn.setDisable(false);
        sizeMenu.setDisable(false);
        algorithmMenu.setDisable(false);
        progressBar.setVisible(false);
        goal1.setDisable(false);
        goal2.setDisable(false);
    }
    // Disable các nút
    private void setDisable() {
        jumbleBtn.setDisable(true);
        addImage.setDisable(true);
        addNumber.setDisable(true);
        compareBtn.setDisable(true);
        sizeMenu.setDisable(true);
        algorithmMenu.setDisable(true);
        progressBar.setVisible(true);
        goal1.setDisable(true);
        goal2.setDisable(true);
    }
    // Mô tả thuật toán + heuristic đang dùng cho bảng kết quả
    private String algorithmLabel() {
        return switch (algorithm) {
            case "BFS" -> "BFS (tìm kiếm theo chiều rộng)";
            case "IDA*" -> "IDA* với Heuristic " + heuristicName(State.heuristic);
            case "IDA*-TT" -> "IDA* + bảng chuyển vị 64 MB với Heuristic " + heuristicName(State.heuristic);
            case "RBFS" -> "RBFS (Recursive Best-First) với Heuristic " + heuristicName(State.heuristic);
            case "SMA*" -> "SMA* (giới hạn 200.000 node) với Heuristic " + heuristicName(State.heuristic);
            case "Weighted A*" -> "Weighted A* (w = 1.5) với Heuristic " + heuristicName(State.heuristic);
            case "Greedy" -> "Greedy Best-First với Heuristic " + heuristicName(State.heuristic);
            case "Bi-BFS" -> "Bidirectional BFS (tìm kiếm hai chiều)";
            case "Value Iteration" -> "Value Iteration - lặp giá trị trên MDP";
            case "Hill Climbing" -> "Hill Climbing - leo đồi (tìm kiếm cục bộ)";
            case "Simulated Annealing" -> "Simulated Annealing - mô phỏng ủ";
            case "Genetic Algorithm" -> "Genetic Algorithm - thuật toán di truyền";
            default -> "A* với Heuristic " + heuristicName(State.heuristic);
        };
    }
    // Tên đầy đủ của từng heuristic
    private String heuristicName(int h) {
        return switch (h) {
            case 1 -> "H1 (số ô sai vị trí)";
            case 2 -> "H2 (Manhattan)";
            case 3 -> "H3 (Euclid)";
            case 4 -> "H4 (sai hàng/cột)";
            case 5 -> "H5 (Manhattan + xung đột tuyến tính)";
            case 6 -> "H6 (H5 + ô bị chặn)";
            case 7 -> "H7 (Walking Distance)";
            case 8 -> "H8 (max(MD+LC chuẩn, Walking Distance))";
            case 9 -> "H9 (Pattern Database)";
            default -> "H" + h;
        };
    }
    // Bảng thông báo không tìm được lời giải
    public void showWarning() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        ButtonType closeTypeBtn = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(closeTypeBtn);
        alert.setTitle("Thông báo");
        alert.setHeaderText("Không tìm được lời giải!");
        alert.setContentText("Nguyên nhân: \n" + error);
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(Objects.requireNonNull(N_PuzzleApplication.class.getResourceAsStream("img/logo.png"))));
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(Objects.requireNonNull(getClass().getResource("style.css")).toExternalForm());
        alert.showAndWait().ifPresent(res -> notSolve());
    }
    // Bảng thông báo kết quả tìm kiếm
    public void showAlert() {
        Alert alert = new Alert(Alert.AlertType.NONE);
        ButtonType runTypeBtn = new ButtonType("Chạy", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeTypeBtn = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.setTitle("Thông báo");
        alert.getButtonTypes().setAll(runTypeBtn, closeTypeBtn);
        alert.setHeaderText("Lời giải: ");
        // Kết quả tìm kiếm được
        alert.setContentText("Thuật toán sử dụng: " + algorithmLabel() + "\n"
            + "Số node đã duyệt: " + approvedNodes + "\n"
            + "Tổng số node trên cây: " + totalNodes + "\n"
            + "Tổng số bước: " + (result.size() - 1) + "\n"
            + "Thời gian tìm kiếm: " + solveTime + "ms" + "\n"
            + "Bạn có muốn chạy lời giải?"
        );
        alertStyle(alert, closeTypeBtn);
        // Hiển thị kết quả và đợi phải hồi
        alert.showAndWait().ifPresent(res -> {
            if (res == runTypeBtn) {
                solveBtn.setDisable(true);
                Thread runResult = new Thread(this, "npuzzle-playback");
                runResult.setDaemon(true);
                runResult.start();
            } else {
                notSolve();
            }
        });
    }
    // Hiển thị bảng so sánh Heuristic
    public void showCompare() {
        Alert compare = new Alert(Alert.AlertType.CONFIRMATION);
        ButtonType runTypeBtn = new ButtonType("Chạy", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeTypeBtn = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);
        compare.setTitle("Thông báo");
        compare.setHeaderText("So Sánh: ");
        // Hiển thị kết quả
        VBox vBox = new VBox();
        vBox.setAlignment(Pos.CENTER);
        vBox.setSpacing(15);
        GridPane gridPane = new GridPane();
        gridPane.setVgap(10);
        gridPane.setHgap(10);
        // Hiển thị kết quả của từng Heuristic
        for (int i = 0; i < compareResults.size(); i++) {
            Result rs = compareResults.get(i);
            Label rsLabel = new Label(rs.showResult());
            GridPane.setConstraints(rsLabel, i % 3, i / 3);
            gridPane.getChildren().add(rsLabel);
        }
        // Sắp xếp và hiển thị kết quả so sánh
        compareResults.sort(Comparator.comparingInt(o -> o.approved));
        List<Result> solvedResults = new ArrayList<>(compareResults.stream().filter(rs -> rs.error == null).toList());
        Collections.reverse(solvedResults);
        Label cpLabel = new Label("Kết luận (từ ít hiệu quả đến hiệu quả hơn): ");
        boolean flag = !solvedResults.isEmpty();
        for (int i = 0; i < solvedResults.size(); i++) {
            cpLabel.setText(cpLabel.getText() + solvedResults.get(i).heuristic);
            if (i < solvedResults.size() - 1) cpLabel.setText(cpLabel.getText() + " < ");
        }
        // Flag kiểm tra xem có tìm được kết quả hay không
        if (flag) {
            cpLabel.setText(cpLabel.getText() + ". Bạn có muốn chạy lời giải?");
            compare.getButtonTypes().setAll(runTypeBtn, closeTypeBtn);
        } else {
            cpLabel.setText(cpLabel.getText() + "Không tìm được lời giải!");
            compare.getButtonTypes().setAll(closeTypeBtn);
        }
        alertStyle(compare, closeTypeBtn);
        vBox.getChildren().addAll(gridPane, cpLabel);
        compare.getDialogPane().setContent(vBox);
        // Chờ phải hồi
        compare.showAndWait().ifPresent(res -> {
            if (res == runTypeBtn) {
                solveBtn.setDisable(true);
                Thread runResult = new Thread(this, "npuzzle-playback");
                runResult.setDaemon(true);
                runResult.start();
            } else {
                notSolve();
            }
        });
        // Clear vector kết quả
        compareResults.clear();
    }
    // Show kết quả người chơi
    public void showResult() {
        long time = (System.currentTimeMillis() - startTime) / 1000;
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText("Bạn đã hoàn thành trò chơi!");
        alert.setContentText("Số bước giải: " + countStep + "\n"
            + "Thời gian giải: " + (time >= 60 ? time / 60 + ":" + time % 60 : time) + "s"
        );
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(Objects.requireNonNull(N_PuzzleApplication.class.getResourceAsStream("img/logo.png"))));
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(Objects.requireNonNull(getClass().getResource("style.css")).toExternalForm());
        alert.showAndWait().ifPresent(res -> notPlay());
    }
    // Thêm icon và style cho bảng lời giải và bảng so sánh
    public void alertStyle(Alert alert, ButtonType closeTypeBtn) {
        // Thêm icon
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(Objects.requireNonNull(N_PuzzleApplication.class.getResourceAsStream("img/logo.png"))));
        DialogPane dialogPane = alert.getDialogPane();
        // Thêm css
        dialogPane.getStylesheets().add(Objects.requireNonNull(getClass().getResource("style.css")).toExternalForm());
        javafx.scene.Node closeBtn = alert.getDialogPane().lookupButton(closeTypeBtn);
        closeBtn.setId("close-btn");
    }
    // Hiển thị ra màn hình
    public void displayImage(Image img) {
        if (img == null) {
            displayPane.setStyle("-fx-background-radius: 20px; -fx-background-color: #703838");
        } else {
            displayPane.setStyle("");
        }
        handledImage = new HandleImage(img ,size, value);
        if (state.isGoal(goalState)) {
            handledImage.win = true;
        }
        GraphicsContext gc = imgCanvas.getGraphicsContext2D();
        handledImage.paint(gc);
    }
}
