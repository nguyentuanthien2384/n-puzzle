package com.example.npuzzleai.verify;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Move;
import com.example.npuzzleai.core.PermutationRank;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bảng khoảng cách chính xác h*(s) cho mọi trạng thái 2x2/3x3, dựng bằng BFS ngược từ đích.
 *
 * <p>Đánh số trạng thái bằng hạng Lehmer của hoán vị (9! = 362.880 ô cho 3x3); trạng thái không
 * tới được ghi -1. Đây là "oracle" độc lập với mọi heuristic và thuật toán, dùng để:
 * kiểm định heuristic (h &lt;= h*, nhất quán), kiểm tra tính tối ưu của solver và kiểm tra
 * bộ kiểm tra khả giải.</p>
 */
public final class ExactDistanceTable {
    private static final Map<Goal, ExactDistanceTable> CACHE = new ConcurrentHashMap<>();

    private final Goal goal;
    private final int size;
    private final int cells;
    private final byte[] dist;
    private final int reachable;
    private final int maxDistance;

    private ExactDistanceTable(Goal goal) {
        this.goal = goal;
        this.size = goal.size();
        this.cells = size * size;
        int space = PermutationRank.factorial(cells);
        this.dist = new byte[space];
        Arrays.fill(dist, (byte) -1);

        int[] queue = new int[space];
        int head = 0, tail = 0;
        int goalRank = PermutationRank.rank(goal.board());
        dist[goalRank] = 0;
        queue[tail++] = goalRank;
        int[] current = new int[cells];
        int max = 0;
        while (head < tail) {
            int r = queue[head++];
            int d = dist[r];
            if (d > max) max = d;
            PermutationRank.unrank(r, cells, current);
            int blank = 0;
            while (current[blank] != 0) blank++;
            int row = blank / size, col = blank % size;
            for (int dir = 0; dir < 4; dir++) {
                int tr = row + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
                int tc = col + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                if (tr < 0 || tr >= size || tc < 0 || tc >= size) continue;
                int target = tr * size + tc;
                current[blank] = current[target];
                current[target] = 0;
                int childRank = PermutationRank.rank(current);
                current[target] = current[blank];
                current[blank] = 0;
                if (dist[childRank] == -1) {
                    dist[childRank] = (byte) (d + 1);
                    queue[tail++] = childRank;
                }
            }
        }
        this.reachable = tail;
        this.maxDistance = max;
    }

    /** Lấy (và cache) bảng cho một đích 2x2/3x3. */
    public static ExactDistanceTable forGoal(Goal goal) {
        if (goal.size() > 3) {
            throw new IllegalArgumentException("Bảng khoảng cách chính xác chỉ hỗ trợ tới 3x3 (4x4 có 10^13 trạng thái)");
        }
        return CACHE.computeIfAbsent(goal, ExactDistanceTable::new);
    }

    public Goal goal() {
        return goal;
    }

    public int permutationCount() {
        return dist.length;
    }

    public int reachableCount() {
        return reachable;
    }

    /** Đường kính không gian trạng thái (31 với 3x3 khi ô trống đích ở góc). */
    public int maxDistance() {
        return maxDistance;
    }

    /** h*(board), hoặc -1 nếu không tới được đích. */
    public int distance(Board board) {
        return dist[PermutationRank.rank(board)];
    }

    public int distanceAt(int rank) {
        return dist[rank];
    }

    public Board boardAt(int rank) {
        int[] values = new int[cells];
        PermutationRank.unrank(rank, cells, values);
        return Board.of(size, values);
    }

    /** Số trạng thái theo từng khoảng cách 0..maxDistance. */
    public long[] histogram() {
        long[] hist = new long[maxDistance + 1];
        for (byte d : dist) if (d >= 0) hist[d]++;
        return hist;
    }

    /** Nước đi tối ưu từ board (một trong các nước giảm h* đúng 1), hoặc null nếu đã ở đích/vô nghiệm. */
    public Move optimalMove(Board board) {
        int d = distance(board);
        if (d <= 0) return null;
        for (Move m : board.legalMoves()) {
            if (distance(board.move(m)) == d - 1) return m;
        }
        return null;
    }
}
