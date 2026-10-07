package com.example.npuzzleai.heuristics;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.BoardGenerator;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.heuristics.pdb.AdditivePatternDatabaseHeuristic;
import com.example.npuzzleai.heuristics.pdb.DefaultPartitions;
import com.example.npuzzleai.heuristics.pdb.PatternDatabase;
import com.example.npuzzleai.heuristics.pdb.PatternDatabaseBuilder;
import com.example.npuzzleai.heuristics.pdb.PatternDefinition;
import com.example.npuzzleai.heuristics.pdb.PatternIndexer;
import com.example.npuzzleai.heuristics.pdb.PdbStore;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.verify.HeuristicReport;
import com.example.npuzzleai.verify.HeuristicVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PatternDatabaseTest {

    @Test
    void indexerIsBijective() {
        int cells = 7, k = 3;
        Set<Integer> ranks = new HashSet<>();
        int[] pos = new int[k];
        int[] back = new int[k];
        for (int a = 0; a < cells; a++) {
            for (int b = 0; b < cells; b++) {
                for (int c = 0; c < cells; c++) {
                    if (a == b || b == c || a == c) continue;
                    pos[0] = a;
                    pos[1] = b;
                    pos[2] = c;
                    int r = PatternIndexer.rank(pos, k, cells);
                    assertTrue(r >= 0 && r < PatternIndexer.count(cells, k));
                    assertTrue(ranks.add(r), "Chỉ số phải duy nhất");
                    PatternIndexer.unrank(r, k, cells, back);
                    assertArrayEquals(pos, back);
                }
            }
        }
        assertEquals(PatternIndexer.count(cells, k), ranks.size());
    }

    @Test
    void additivePartitionAdmissibleAndDominatesManhattan() {
        for (Goal goal : List.of(Goal.standard(3), Goal.blankFirst(3))) {
            Heuristic apdb = HeuristicRegistry.defaults().create("apdb:3x3@1,2,3,4;5,6,7,8");
            HeuristicReport r = HeuristicVerifier.verify(apdb, goal);
            assertEquals(0, r.admissibilityViolations(), "PDB 4-4 rời nhau phải chấp nhận được");
            double[] dom = HeuristicVerifier.dominance(apdb, HeuristicRegistry.defaults().create("manhattan"), goal);
            assertEquals(0.0, dom[2], "Mỗi pattern đếm ít nhất Manhattan của các ô trong nó");
        }
    }

    @Test
    void singlePatternIsAdmissible() {
        HeuristicReport r = HeuristicVerifier.verify(HeuristicRegistry.defaults().create("pdb:3x3@1,2,3"), Goal.standard(3));
        assertEquals(0, r.admissibilityViolations());
        assertTrue(r.meanH() > 0);
    }

    @Test
    void overlappingPatternsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> AdditivePatternDatabaseHeuristic.explicit("1,2,3;3,4,5", 3));
        assertThrows(IllegalArgumentException.class, () -> new PatternDefinition(3, new int[]{0, 1}));
        assertThrows(IllegalArgumentException.class, () -> new PatternDefinition(3, new int[]{1, 1}));
    }

    @Test
    void defaultPartitionsCoverAllTilesDisjointly() {
        for (int size = 2; size <= 5; size++) {
            for (Goal goal : List.of(Goal.standard(size), Goal.blankFirst(size),
                    Goal.of(BoardGenerator.uniformSolvable(Goal.standard(size), new Random(size))))) {
                List<PatternDefinition> patterns = DefaultPartitions.forGoal(goal);
                PatternDefinition.requireDisjoint(patterns);
                int covered = patterns.stream().mapToInt(PatternDefinition::tileCount).sum();
                assertEquals(size * size - 1, covered, "Phân hoạch phải phủ mọi ô số của " + size + "x" + size);
            }
        }
    }

    @Test
    void fileRoundTripWithChecksum(@TempDir Path dir) throws IOException {
        Goal goal = Goal.standard(3);
        PatternDefinition pattern = new PatternDefinition(3, new int[]{1, 2, 3, 4});
        PatternDatabase db = PatternDatabaseBuilder.build(pattern, goal);
        Path file = dir.resolve(PdbStore.fileName(pattern, goal));
        PdbStore.write(db, file);

        PatternDatabase loaded = PdbStore.read(file);
        assertTrue(loaded.metadata().matches(pattern, goal));
        assertFalse(loaded.metadata().matches(pattern, Goal.blankFirst(3)), "Không được dùng PDB của đích khác");
        assertEquals(db.metadata().checksum(), loaded.metadata().checksum());
        Random rnd = new Random(3);
        for (int i = 0; i < 200; i++) {
            Board b = BoardGenerator.uniformSolvable(goal, rnd);
            assertEquals(db.lookup(b.positions()), loaded.lookup(b.positions()));
        }

        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 10] ^= 0x5A; // làm hỏng một byte của bảng
        Files.write(file, bytes);
        assertThrows(IOException.class, () -> PdbStore.read(file), "Checksum phải phát hiện file bị sửa");
    }

    @Test
    void default4x4PdbIsZeroAtGoalAndOneNextToIt() {
        Goal goal = Goal.standard(4);
        Heuristic apdb = HeuristicRegistry.defaults().create("apdb");
        assertEquals(0, apdb.estimate(goal.board(), goal));
        for (var m : goal.board().legalMoves()) {
            assertEquals(1, apdb.estimate(goal.board().move(m), goal), "Cách đích 1 nước thì PDB cộng phải bằng 1");
        }
    }

    @Test
    void smallPatternOn5x5() {
        // Pattern 2 ô trên 5x5: P(25, 3) = 13.800 trạng thái - kiểm tra builder với ô > 16 (mặt nạ 64 bit).
        Goal goal = Goal.standard(5);
        Heuristic pdb = HeuristicRegistry.defaults().create("pdb:5x5@23,24");
        assertEquals(0, pdb.estimate(goal.board(), goal));
        Board moved = goal.board().move(com.example.npuzzleai.core.Move.LEFT); // ô 24 trượt sang phải
        assertEquals(1, pdb.estimate(moved, goal));
        Random rnd = new Random(5);
        for (int i = 0; i < 300; i++) {
            Board b = BoardGenerator.uniformSolvable(goal, rnd);
            assertTrue(pdb.estimate(b, goal) >= manhattanOf(b, goal, 23) + manhattanOf(b, goal, 24),
                    "PDB của pattern phải >= tổng Manhattan các ô trong pattern");
        }
    }

    private static int manhattanOf(Board b, Goal goal, int tile) {
        int pos = b.positions()[tile];
        return Math.abs(pos / b.size() - goal.rowOf(tile)) + Math.abs(pos % b.size() - goal.colOf(tile));
    }
}
