module com.example.npuzzleai {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires java.management;
    requires jdk.management;

    opens com.example.npuzzleai to javafx.fxml;
    exports com.example.npuzzleai;

    // Search engine độc lập JavaFX - GUI, CLI, benchmark và plugin dùng chung.
    // Phân lớp một chiều (không vòng phụ thuộc giữa các gói):
    // core <- search <- {algorithms, heuristics(.pdb), verify} <- learning <- benchmark <- research <- {cli, ui}.
    exports com.example.npuzzleai.core;
    exports com.example.npuzzleai.search;
    exports com.example.npuzzleai.algorithms;
    exports com.example.npuzzleai.heuristics;
    exports com.example.npuzzleai.heuristics.pdb;
    exports com.example.npuzzleai.verify;
    exports com.example.npuzzleai.learning;
    exports com.example.npuzzleai.research;
    exports com.example.npuzzleai.benchmark;
    exports com.example.npuzzleai.cli;
    exports com.example.npuzzleai.ui;

    // Plugin thuật toán/heuristic nạp qua ServiceLoader.
    uses com.example.npuzzleai.search.SearchAlgorithm;
    uses com.example.npuzzleai.search.Heuristic;

    // Gói học máy tự đăng ký như một plugin để heuristics/algorithms không phụ thuộc ngược vào nó.
    // (Chế độ classpath dùng META-INF/services tương ứng.)
    provides com.example.npuzzleai.search.SearchAlgorithm with com.example.npuzzleai.learning.FocalSearch;
    provides com.example.npuzzleai.search.Heuristic with com.example.npuzzleai.learning.LearnedHeuristic;
}
