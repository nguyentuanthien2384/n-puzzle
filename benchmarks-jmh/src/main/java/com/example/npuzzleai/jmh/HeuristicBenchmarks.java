package com.example.npuzzleai.jmh;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.BoardGenerator;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Move;
import com.example.npuzzleai.heuristics.HeuristicRegistry;
import com.example.npuzzleai.search.Heuristic;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Bốn phép toán nguyên thuỷ cần đo trước khi quyết định tối ưu cấu trúc dữ liệu:
 * sinh successor, hash/equals (tra tập đóng), mã hoá khoá nén, và đánh giá heuristic.
 * Chỉ giữ lại một tối ưu (packed state, incremental Manhattan...) khi JMH chứng minh có lợi.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class HeuristicBenchmarks {
    private static final int POOL = 1024;

    @Param({"3", "4", "5"})
    public int size;

    private Goal goal;
    private Board[] boards;
    private Set<Board> closed;
    private Heuristic manhattan;
    private Heuristic linearConflict;
    private Heuristic apdb;
    private int cursor;

    @Setup(Level.Trial)
    public void setup() {
        goal = Goal.standard(size);
        Random rnd = new Random(42);
        boards = new Board[POOL];
        closed = new HashSet<>();
        for (int i = 0; i < POOL; i++) {
            boards[i] = BoardGenerator.uniformSolvable(goal, rnd);
            if (i % 2 == 0) closed.add(boards[i]);
        }
        manhattan = HeuristicRegistry.defaults().create("manhattan");
        linearConflict = HeuristicRegistry.defaults().create("linear-conflict");
        apdb = HeuristicRegistry.defaults().create("apdb");
        apdb.prepare(goal); // dựng PDB ngoài phép đo
    }

    private Board next() {
        cursor = (cursor + 1) & (POOL - 1);
        return boards[cursor];
    }

    @Benchmark
    public int manhattan() {
        return manhattan.estimate(next(), goal);
    }

    @Benchmark
    public int linearConflict() {
        return linearConflict.estimate(next(), goal);
    }

    @Benchmark
    public int additivePdb() {
        return apdb.estimate(next(), goal);
    }

    @Benchmark
    public void generateSuccessors(Blackhole bh) {
        Board b = next();
        for (int d = 0; d < Move.COUNT; d++) {
            Move m = Move.byOrdinal(d);
            if (b.canMove(m)) bh.consume(b.move(m));
        }
    }

    @Benchmark
    public boolean closedSetLookup() {
        return closed.contains(next());
    }

    @Benchmark
    public long compactKey() {
        Board b = next();
        return b.keyLow() ^ b.keyHigh();
    }
}
