module com.example.npuzzleai {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires java.management;
    requires jdk.management;

    opens com.example.npuzzleai to javafx.fxml;
    exports com.example.npuzzleai;

    // Search engine độc lập JavaFX - GUI, CLI, benchmark và plugin dùng chung.
    exports com.example.npuzzleai.core;
    exports com.example.npuzzleai.search;
    exports com.example.npuzzleai.algorithms;
    exports com.example.npuzzleai.heuristics;
    exports com.example.npuzzleai.heuristics.pdb;
    exports com.example.npuzzleai.verify;
    exports com.example.npuzzleai.benchmark;
    exports com.example.npuzzleai.cli;
    exports com.example.npuzzleai.ui;

    // Plugin thuật toán/heuristic nạp qua ServiceLoader.
    uses com.example.npuzzleai.search.SearchAlgorithm;
    uses com.example.npuzzleai.search.Heuristic;
}
