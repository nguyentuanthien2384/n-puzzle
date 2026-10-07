package com.example.npuzzleai.heuristics;

import com.example.npuzzleai.core.BoardGenerator;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.verify.HeuristicReport;
import com.example.npuzzleai.verify.HeuristicVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm định vét cạn: mọi heuristic khai báo admissible/consistent phải đúng trên toàn bộ
 * 181.440 trạng thái 3x3 và mọi cạnh, với ba đích (chuẩn, ô trống trước, tuỳ ý).
 * Đây là regression test chống "heuristic tưởng admissible nhưng thực tế đếm dư".
 */
class HeuristicPropertiesTest {
    private static final Goal CUSTOM_GOAL = Goal.of(BoardGenerator.uniformSolvable(Goal.standard(3), new Random(7)));

    static Stream<Arguments> heuristicsAndGoals() {
        List<Arguments> args = new ArrayList<>();
        for (String id : HeuristicRegistry.defaults().defaultIds()) {
            if (!HeuristicRegistry.defaults().create(id).supports(3)) continue;
            for (Goal g : List.of(Goal.standard(3), Goal.blankFirst(3), CUSTOM_GOAL)) args.add(Arguments.of(id, g));
        }
        return args.stream();
    }

    @ParameterizedTest(name = "{0} @ {1}")
    @MethodSource("heuristicsAndGoals")
    void declaredPropertiesHoldExhaustively(String id, Goal goal) {
        Heuristic h = HeuristicRegistry.defaults().create(id);
        HeuristicReport r = HeuristicVerifier.verify(h, goal);
        assertEquals(181_440, r.states());
        assertEquals(0, r.goalValue(), "h(đích) phải bằng 0");
        if (r.claimedAdmissible()) {
            assertEquals(0, r.admissibilityViolations(),
                    id + " khai báo admissible nhưng có phản ví dụ [" + r.counterexample() + "] h="
                            + r.counterexampleH() + " > h*=" + r.counterexampleHStar());
        }
        if (r.claimedConsistent()) {
            assertEquals(0, r.consistencyViolations(), id + " khai báo consistent nhưng vi phạm trên cạnh");
        }
        assertTrue(r.claimsHold());
    }

    @Test
    void exactPatternDatabaseOn3x3EqualsHStar() {
        HeuristicReport r = HeuristicVerifier.verify(HeuristicRegistry.defaults().create("apdb"), Goal.standard(3));
        assertEquals(1.0, r.exactRate(), 1e-12, "PDB đầy đủ 8 ô phải bằng đúng h*");
        assertTrue(r.claimedConsistent());
    }

    @Test
    void dominanceRelations() {
        Goal goal = Goal.blankFirst(3);
        Heuristic md = HeuristicRegistry.defaults().create("manhattan");
        double[] lcVsMd = HeuristicVerifier.dominance(HeuristicRegistry.defaults().create("linear-conflict"), md, goal);
        assertEquals(0.0, lcVsMd[2], "Linear Conflict không bao giờ nhỏ hơn Manhattan");
        assertTrue(lcVsMd[0] > 0, "Linear Conflict phải lớn hơn Manhattan ở một số trạng thái");

        double[] wdVsMd = HeuristicVerifier.dominance(HeuristicRegistry.defaults().create("walking-distance"), md, goal);
        assertEquals(0.0, wdVsMd[2], "Walking Distance không bao giờ nhỏ hơn Manhattan");

        double[] mdVsMisplaced = HeuristicVerifier.dominance(md, HeuristicRegistry.defaults().create("misplaced"), goal);
        assertEquals(0.0, mdVsMisplaced[2], "Manhattan trội số ô sai vị trí");
    }

    @Test
    void legacyHeuristicsAreFlaggedExperimental() {
        for (String id : List.of("legacy-h5", "legacy-h6")) {
            Heuristic h = HeuristicRegistry.defaults().create(id);
            assertTrue(h.properties().experimental());
            assertFalse(h.properties().claimedAdmissible(), id + " chưa được chứng minh nên không được khai báo admissible");
        }
    }

    @Test
    void legacyIndexMapping() {
        assertEquals("manhattan", HeuristicRegistry.legacyId(2));
        assertEquals("apdb", HeuristicRegistry.legacyId(9));
        assertEquals("manhattan", HeuristicRegistry.legacyId(42));
    }
}
