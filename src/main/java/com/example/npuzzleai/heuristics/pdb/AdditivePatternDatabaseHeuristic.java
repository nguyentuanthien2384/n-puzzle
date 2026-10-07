package com.example.npuzzleai.heuristics.pdb;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.HeuristicProperties;

import java.util.List;
import java.util.function.Function;

/**
 * Heuristic Pattern Database: một pattern (PDB đơn) hoặc nhiều pattern rời nhau (additive/disjoint PDB,
 * Korf-Felner 2002). Các pattern được kiểm tra đôi một rời nhau trước khi cộng; với mô hình chi phí
 * "pattern-moves", tổng là cận dưới chấp nhận được.
 *
 * <p>Tính nhất quán: bảng đã lấy min theo vị trí ô trống nên có thể <b>không</b> nhất quán, trừ khi
 * pattern phủ mọi ô (PDB đầy đủ 3x3 = h*). Vì vậy A* cần reopen; IDA*, RBFS, SMA* không bị ảnh hưởng.</p>
 */
public final class AdditivePatternDatabaseHeuristic implements Heuristic {
    private final String id;
    private final String displayName;
    private final Function<Goal, List<PatternDefinition>> partitionFor;
    private final Integer fixedSize;
    private volatile Prepared prepared;

    private record Prepared(Goal goal, PatternDatabase[] databases, boolean coversAllTiles) {
    }

    private AdditivePatternDatabaseHeuristic(String id, String displayName,
                                             Function<Goal, List<PatternDefinition>> partitionFor, Integer fixedSize) {
        this.id = id;
        this.displayName = displayName;
        this.partitionFor = partitionFor;
        this.fixedSize = fixedSize;
    }

    /** Phân hoạch mặc định theo đích ({@link DefaultPartitions}). */
    public static AdditivePatternDatabaseHeuristic defaultPartition() {
        return new AdditivePatternDatabaseHeuristic("apdb", "H9 Additive PDB (mặc định)", DefaultPartitions::forGoal, null);
    }

    /** Phân hoạch tường minh, ví dụ "1,2,3,4,5;6,7,8,9,10;11,12,13,14,15". */
    public static AdditivePatternDatabaseHeuristic explicit(String spec, int size) {
        List<PatternDefinition> patterns = PatternDefinition.parsePartition(spec, size);
        PatternDefinition.requireDisjoint(patterns);
        String prefix = patterns.size() == 1 ? "pdb" : "apdb";
        String label = patterns.size() == 1 ? "PDB đơn " : "Additive PDB ";
        return new AdditivePatternDatabaseHeuristic(prefix + ":" + spec.replace(" ", ""),
                label + patterns, g -> patterns, size);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean supports(int size) {
        return fixedSize != null ? fixedSize == size : DefaultPartitions.supports(size);
    }

    @Override
    public HeuristicProperties properties() {
        Prepared p = prepared;
        boolean exact = p != null && p.coversAllTiles();
        return new HeuristicProperties(true, exact, true, false,
                "Pattern rời nhau, chỉ tính nước đi của ô pattern - cộng được (Korf-Felner)");
    }

    @Override
    public void prepare(Goal goal) {
        prepareFor(goal);
    }

    @Override
    public int estimate(Board board, Goal goal) {
        Prepared p = prepared;
        if (p == null || !p.goal().equals(goal)) p = prepareFor(goal);
        int[] positions = board.positions();
        int h = 0;
        for (PatternDatabase db : p.databases()) h += db.lookup(positions);
        return h;
    }

    /** Metadata các bảng đã nạp cho đích hiện tại (để ghi vào manifest thí nghiệm). */
    public List<PdbMetadata> metadata() {
        Prepared p = prepared;
        if (p == null) return List.of();
        return java.util.Arrays.stream(p.databases()).map(PatternDatabase::metadata).toList();
    }

    public long totalBytes() {
        Prepared p = prepared;
        if (p == null) return 0;
        long sum = 0;
        for (PatternDatabase db : p.databases()) sum += db.bytes();
        return sum;
    }

    private synchronized Prepared prepareFor(Goal goal) {
        Prepared p = prepared;
        if (p != null && p.goal().equals(goal)) return p;
        List<PatternDefinition> patterns = partitionFor.apply(goal);
        PatternDefinition.requireDisjoint(patterns);
        PatternDatabase[] dbs = new PatternDatabase[patterns.size()];
        int covered = 0;
        for (int i = 0; i < dbs.length; i++) {
            PatternDefinition def = patterns.get(i);
            if (def.size() != goal.size()) {
                throw new IllegalArgumentException("Pattern dành cho bảng " + def.size() + "x" + def.size());
            }
            dbs[i] = PdbStore.get(def, goal);
            covered += def.tileCount();
        }
        p = new Prepared(goal, dbs, covered == goal.size() * goal.size() - 1 && dbs.length == 1);
        prepared = p;
        return p;
    }
}
