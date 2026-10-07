package com.example.npuzzleai.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NPuzzleCliTest {

    private static String run(int expectedCode, String... args) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        int code = new NPuzzleCli(out).run(args);
        String text = buffer.toString(StandardCharsets.UTF_8);
        assertEquals(expectedCode, code, text);
        return text;
    }

    @Test
    void listSplittingKeepsPartitionParameters() {
        assertEquals(List.of("astar", "wastar:2.0", "apdb:1,2,3;4,5,6", "ida"),
                NPuzzleCli.csv("astar, wastar:2.0,apdb:1,2,3;4,5,6,ida"));
        assertEquals(Map.of("board", "1 2 3", "show-path", "true"),
                NPuzzleCli.parseOptions(new String[]{"--board", "1 2 3", "--show-path"}));
    }

    @Test
    void solveReportsOptimalSolution() throws IOException {
        String out = run(0, "solve", "--board", "1 2 3 4 5 6 0 7 8", "--algo", "astar", "--heuristic", "manhattan");
        assertTrue(out.contains("SOLVED"));
        assertTrue(out.contains("✓ tối ưu"));
    }

    @Test
    void verifySubsetPasses() throws IOException {
        String out = run(0, "verify", "--heuristics", "manhattan,linear-conflict");
        assertTrue(out.contains("Admissible + Consistent"));
    }

    @Test
    void datasetAndBenchmarkCommands(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("easy.txt");
        run(0, "dataset", "--name", "easy-3x3", "--seed", "1", "--out", file.toString());
        assertTrue(Files.readString(file).contains("# name=easy-3x3"));
        String out = run(0, "benchmark", "--dataset", "file:" + file, "--algos", "ida", "--heuristics", "linear-conflict",
                "--warmup", "0", "--out", dir.resolve("exp").toString());
        assertTrue(out.contains("ida + linear-conflict"));
    }

    @Test
    void trainAndUseLearnedHeuristic(@TempDir Path dir) throws IOException {
        Path model = dir.resolve("l3.model");
        String out = run(0, "train-heuristic", "--size", "3", "--epochs", "3", "--out", model.toString());
        assertTrue(out.contains("RMSE"));
        assertTrue(Files.isRegularFile(model));
        String solved = run(0, "solve", "--board", "1,2,3,4,5,6,0,7,8", "--algo", "astar", "--heuristic", "learned:" + model);
        assertTrue(solved.contains("SOLVED"));
    }

    @Test
    void adversarialCommandWritesDataset(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("adv.txt");
        String out = run(0, "adversarial", "--size", "3", "--objective", "gap", "--population", "8",
                "--generations", "3", "--top", "4", "--out", file.toString());
        assertTrue(out.contains("Top 4"));
        assertTrue(Files.readString(file).contains("# name=adversarial-heuristic_gap"));
    }

    @Test
    void unknownCommandFails() throws IOException {
        run(2, "khong-ton-tai");
    }
}
