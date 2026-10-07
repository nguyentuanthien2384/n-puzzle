package com.example.npuzzleai.heuristics;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.HeuristicProperties;

import java.util.List;

/**
 * max(h1, h2, ...) - luôn an toàn: max của các heuristic chấp nhận được là chấp nhận được,
 * max của các heuristic nhất quán là nhất quán. Khác với cộng, không cần điều kiện phân hoạch chi phí.
 */
public final class MaxHeuristic implements Heuristic {
    private final String id;
    private final String displayName;
    private final List<Heuristic> parts;
    private final HeuristicProperties properties;

    public MaxHeuristic(String id, String displayName, List<Heuristic> parts) {
        if (parts.isEmpty()) throw new IllegalArgumentException("Cần ít nhất một heuristic");
        this.id = id;
        this.displayName = displayName;
        this.parts = List.copyOf(parts);
        boolean admissible = true, consistent = true, preprocessing = false, experimental = false;
        for (Heuristic h : parts) {
            HeuristicProperties p = h.properties();
            admissible &= p.claimedAdmissible();
            consistent &= p.claimedConsistent();
            preprocessing |= p.requiresPreprocessing();
            experimental |= p.experimental();
        }
        this.properties = new HeuristicProperties(admissible, consistent, preprocessing, experimental,
                "max của các thành phần");
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
    public int estimate(Board board, Goal goal) {
        int best = 0;
        for (Heuristic h : parts) {
            int v = h.estimate(board, goal);
            if (v > best) best = v;
        }
        return best;
    }

    @Override
    public boolean supports(int size) {
        for (Heuristic h : parts) if (!h.supports(size)) return false;
        return true;
    }

    @Override
    public void prepare(Goal goal) {
        for (Heuristic h : parts) h.prepare(goal);
    }

    @Override
    public HeuristicProperties properties() {
        return properties;
    }
}
