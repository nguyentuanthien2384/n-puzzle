package com.example.npuzzleai.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {

    @Test
    void parseAndToStringRoundTrip() {
        Board b = Board.parse("1 2 3, 4 5 6; 7 0 8");
        assertEquals(3, b.size());
        assertEquals(7, b.blankIndex());
        assertEquals(b, Board.parse(b.toString()));
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8}, b.toArray());
    }

    @Test
    void rejectsInvalidBoards() {
        assertThrows(IllegalArgumentException.class, () -> Board.parse("1 2 3 4 5 6 7 8 8"));
        assertThrows(IllegalArgumentException.class, () -> Board.parse("1 2 3 4 5 6 7 8"));
        assertThrows(IllegalArgumentException.class, () -> Board.of(3, 0, 1, 2, 3, 4, 5, 6, 7, 9));
        assertThrows(IllegalArgumentException.class, () -> Board.parse("a b c d"));
    }

    @Test
    void movesAreImmutableAndReversible() {
        Board b = Board.parse("1 2 3 4 0 5 6 7 8");
        for (Move m : b.legalMoves()) {
            Board next = b.move(m);
            assertNotEquals(b, next);
            assertEquals(b, next.move(m.opposite()), "Đi rồi đi ngược phải quay về");
            assertEquals(m, Move.between(b, next));
        }
        assertEquals(Board.parse("1 2 3 4 0 5 6 7 8"), b, "Board gốc không được thay đổi");
    }

    @Test
    void legalMovesAtCornerAndEdge() {
        Board corner = Board.parse("0 1 2 3 4 5 6 7 8");
        assertEquals(List.of(Move.DOWN, Move.RIGHT), corner.legalMoves());
        assertThrows(IllegalStateException.class, () -> corner.move(Move.UP));
        Board edge = Board.parse("1 0 2 3 4 5 6 7 8");
        assertEquals(3, edge.legalMoves().size());
    }

    @Test
    void moveSequenceFormatting() {
        List<Move> moves = Move.parseSequence("RRDD");
        assertEquals("RRDD", Move.format(moves));
        Board start = Goal.blankFirst(3).board();
        Board end = start.apply(moves);
        assertEquals(8, end.blankIndex());
    }

    @Test
    void compactKeysAreInjective() {
        for (int size : new int[]{3, 4, 5}) {
            Goal goal = Goal.standard(size);
            Random rnd = new Random(size);
            Map<String, Board> seen = new HashMap<>();
            for (int i = 0; i < 3000; i++) {
                Board b = BoardGenerator.uniformSolvable(goal, rnd);
                String key = b.keyLow() + ":" + b.keyHigh();
                Board previous = seen.put(key, b);
                if (previous != null) assertEquals(previous, b, "Hai bảng khác nhau không được trùng khoá nén");
            }
        }
    }

    @Test
    void goalPositionsAndNames() {
        Goal standard = Goal.standard(3);
        assertEquals(Goal.STANDARD, standard.name());
        assertEquals(0, standard.positionOf(1));
        assertEquals(8, standard.blankPosition());
        assertEquals(2, standard.rowOf(8));
        assertEquals(1, standard.colOf(8));

        Goal blankFirst = Goal.blankFirst(4);
        assertEquals(Goal.BLANK_FIRST, blankFirst.name());
        assertEquals(0, blankFirst.blankPosition());
        assertEquals(15, blankFirst.positionOf(15));

        Goal custom = Goal.of(Board.parse("8 7 6 5 4 3 2 1 0"));
        assertEquals(Goal.CUSTOM, custom.name());
        assertEquals(Goal.fromLegacy(1, 3), Goal.blankFirst(3));
        assertEquals(Goal.fromLegacy(2, 3), Goal.standard(3));
        assertEquals(custom, Goal.parse("8 7 6 5 4 3 2 1 0", 3));
    }

    @Test
    void randomWalkIsDeterministicWithSeed() {
        Goal goal = Goal.standard(4);
        assertEquals(BoardGenerator.randomWalk(goal, 50, new Random(99)), BoardGenerator.randomWalk(goal, 50, new Random(99)));
    }
}
