package com.example.npuzzleai.benchmark;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.BoardGenerator;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.verify.ExactDistanceTable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Dataset dựng sẵn (sinh có seed) và đọc/ghi file văn bản.
 *
 * <p>Định dạng file: các dòng "# khoá=giá trị" (name, size, goal, seed, description), sau đó mỗi
 * dòng một trạng thái, tuỳ chọn "| độ_dài_tối_ưu" ở cuối.</p>
 */
public final class Datasets {
    public static final long DEFAULT_SEED = 20261006L;

    private static final Map<String, String> BUILT_IN = new LinkedHashMap<>();

    static {
        BUILT_IN.put("easy-3x3", "50 bảng 3x3 đi ngẫu nhiên 30 bước từ đích");
        BUILT_IN.put("exact-3x3", "100 bảng 3x3 ngẫu nhiên đều, có độ dài tối ưu chính xác");
        BUILT_IN.put("custom-goal-3x3", "30 bảng 3x3 với đích là một hoán vị ngẫu nhiên");
        BUILT_IN.put("unsolvable-3x3", "20 bảng 3x3 vô nghiệm (đổi chỗ hai ô số)");
        BUILT_IN.put("random-15p", "40 bảng 4x4 đi ngẫu nhiên 60 bước");
        BUILT_IN.put("hard-15p", "20 bảng 4x4 ngẫu nhiên đều (khó, độ dài trung bình ~52)");
        BUILT_IN.put("unsolvable-15p", "20 bảng 4x4 vô nghiệm");
        BUILT_IN.put("random-24p", "15 bảng 5x5 đi ngẫu nhiên 50 bước");
    }

    private Datasets() {
    }

    /** Tên -&gt; mô tả của các dataset dựng sẵn. */
    public static Map<String, String> builtInNames() {
        return new LinkedHashMap<>(BUILT_IN);
    }

    /** Sinh dataset dựng sẵn theo tên và seed; 3x3 được gắn sẵn độ dài tối ưu chính xác. */
    public static Dataset builtIn(String name, long seed) {
        Random rnd = new Random(seed);
        Dataset ds = switch (name) {
            case "easy-3x3" -> walk(name, Goal.standard(3), 50, 30, rnd, seed);
            case "exact-3x3" -> uniform(name, Goal.standard(3), 100, rnd, seed);
            case "custom-goal-3x3" -> {
                Board goalBoard = BoardGenerator.uniformSolvable(Goal.standard(3), rnd);
                yield uniform(name, Goal.of(goalBoard), 30, rnd, seed);
            }
            case "unsolvable-3x3" -> unsolvable(name, Goal.standard(3), 20, rnd, seed);
            case "random-15p" -> walk(name, Goal.standard(4), 40, 60, rnd, seed);
            case "hard-15p" -> uniform(name, Goal.standard(4), 20, rnd, seed);
            case "unsolvable-15p" -> unsolvable(name, Goal.standard(4), 20, rnd, seed);
            case "random-24p" -> walk(name, Goal.standard(5), 15, 50, rnd, seed);
            default -> throw new IllegalArgumentException("Không có dataset '" + name + "'. Có: "
                    + String.join(", ", BUILT_IN.keySet()));
        };
        return withExactLengthsIfSmall(ds);
    }

    /** Đọc "file:đường_dẫn" hoặc tên dataset dựng sẵn. */
    public static Dataset resolve(String spec, long seed) throws IOException {
        if (spec.startsWith("file:")) return withExactLengthsIfSmall(read(Path.of(spec.substring(5))));
        return builtIn(spec, seed);
    }

    public static Dataset walk(String name, Goal goal, int count, int steps, Random rnd, long seed) {
        List<Board> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(BoardGenerator.randomWalk(goal, steps, rnd));
        return new Dataset(name, count + " bảng đi ngẫu nhiên " + steps + " bước", goal, seed, list, null);
    }

    public static Dataset uniform(String name, Goal goal, int count, Random rnd, long seed) {
        List<Board> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(BoardGenerator.uniformSolvable(goal, rnd));
        return new Dataset(name, count + " bảng ngẫu nhiên đều (khả giải)", goal, seed, list, null);
    }

    public static Dataset unsolvable(String name, Goal goal, int count, Random rnd, long seed) {
        List<Board> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(BoardGenerator.unsolvable(goal, rnd));
        return new Dataset(name, count + " bảng vô nghiệm", goal, seed, list, null);
    }

    /** Với 2x2/3x3, điền độ dài tối ưu từ bảng khoảng cách chính xác (vô nghiệm = -1). */
    public static Dataset withExactLengthsIfSmall(Dataset ds) {
        if (ds.size() > 3 || ds.optimalLength() != null) return ds;
        ExactDistanceTable table = ExactDistanceTable.forGoal(ds.goal());
        int[] lengths = new int[ds.instances().size()];
        for (int i = 0; i < lengths.length; i++) lengths[i] = table.distance(ds.instances().get(i));
        return ds.withOptimalLengths(lengths);
    }

    public static void write(Dataset ds, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("# npuzzle-dataset v1\n");
            w.write("# name=" + ds.name() + "\n");
            w.write("# description=" + ds.description() + "\n");
            w.write("# size=" + ds.size() + "\n");
            w.write("# goal=" + ds.goal().board() + "\n");
            w.write("# seed=" + ds.seed() + "\n");
            w.write("# checksum=" + Long.toHexString(ds.checksum()) + "\n");
            for (int i = 0; i < ds.instances().size(); i++) {
                w.write(ds.instances().get(i).toString());
                int opt = ds.optimal(i);
                if (opt >= 0) w.write(" | " + opt);
                w.write("\n");
            }
        }
    }

    public static Dataset read(Path file) throws IOException {
        String name = file.getFileName().toString();
        String description = "Nạp từ " + file;
        Goal goal = null;
        long seed = 0;
        int size = -1;
        List<Board> boards = new ArrayList<>();
        List<Integer> optimal = new ArrayList<>();
        boolean anyOptimal = false;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                String kv = line.substring(1).trim();
                int eq = kv.indexOf('=');
                if (eq < 0) continue;
                String key = kv.substring(0, eq).trim();
                String value = kv.substring(eq + 1).trim();
                switch (key) {
                    case "name" -> name = value;
                    case "description" -> description = value;
                    case "size" -> size = Integer.parseInt(value);
                    case "seed" -> seed = Long.parseLong(value);
                    case "goal" -> goal = Goal.of(Board.parse(value));
                    default -> {
                    }
                }
                continue;
            }
            String boardPart = line;
            int bar = line.indexOf('|');
            int opt = -1;
            if (bar >= 0) {
                boardPart = line.substring(0, bar);
                opt = Integer.parseInt(line.substring(bar + 1).trim());
                anyOptimal = true;
            }
            Board b = Board.parse(boardPart);
            boards.add(b);
            optimal.add(opt);
        }
        if (boards.isEmpty()) throw new IOException("Dataset rỗng: " + file);
        if (size < 0) size = boards.get(0).size();
        if (goal == null) goal = Goal.standard(size);
        int[] lengths = null;
        if (anyOptimal) {
            lengths = new int[optimal.size()];
            for (int i = 0; i < lengths.length; i++) lengths[i] = optimal.get(i);
        }
        return new Dataset(name, description, goal, seed, boards, lengths);
    }
}
