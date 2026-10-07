package com.example.npuzzleai.algorithms;

import com.example.npuzzleai.core.Board;
import com.example.npuzzleai.core.Goal;
import com.example.npuzzleai.core.Move;
import com.example.npuzzleai.core.PuzzleProblem;
import com.example.npuzzleai.search.AlgorithmProperties;
import com.example.npuzzleai.search.Heuristic;
import com.example.npuzzleai.search.SearchAlgorithm;
import com.example.npuzzleai.search.SearchBudget;
import com.example.npuzzleai.search.SearchContext;
import com.example.npuzzleai.search.SearchMetrics;
import com.example.npuzzleai.search.SearchObserver;
import com.example.npuzzleai.search.SearchResult;
import com.example.npuzzleai.search.SearchStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HDA* - Hash Distributed A* (Kishimoto, Fukunaga &amp; Botea 2009), bản proof-of-concept trên đa nhân.
 *
 * <p>Mỗi trạng thái thuộc về đúng một worker theo hash; worker giữ OPEN/CLOSED riêng. Khi sinh successor,
 * worker gửi nó (kèm g và con trỏ cha) vào hàng đợi của worker sở hữu. Để điều kiện dừng đơn giản và
 * chứng minh được, các worker chạy theo vòng đồng bộ (bulk-synchronous): mỗi vòng nhận tin nhắn, mở rộng
 * tối đa {@code BATCH} node, rồi rào chắn.</p>
 *
 * <p>Dừng khi không còn tin nhắn đang truyền và f nhỏ nhất của mọi OPEN ≥ chi phí lời giải tốt nhất đã
 * tìm (incumbent) - khi đó mọi lời giải tốt hơn phải đi qua một node có f &lt; incumbent, mâu thuẫn; nên
 * lời giải tối ưu khi h chấp nhận được (có reopen nên không cần h nhất quán).</p>
 *
 * <p>Metrics riêng: messages (trạng thái chuyển giữa worker), workers, loadImbalancePct
 * (max/trung bình số node mở rộng × 100), iterations (= số vòng đồng bộ). Thứ tự nhận tin trong một vòng
 * phụ thuộc lập lịch luồng nên số node mở rộng có thể dao động nhẹ giữa các lần chạy; độ dài lời giải thì không.</p>
 */
public final class HashDistributedAStar implements SearchAlgorithm {
    private static final int INF = Integer.MAX_VALUE;
    private static final int BATCH = 128;

    private final int workers;
    private final AlgorithmProperties properties;

    public HashDistributedAStar(int workers) {
        if (workers < 1 || workers > 256) throw new IllegalArgumentException("Số worker phải trong 1..256");
        this.workers = workers;
        this.properties = new AlgorithmProperties(true, true, false, 5, "Song song (hash-distributed)");
    }

    private static long cpuNow() {
        try {
            var threads = java.lang.management.ManagementFactory.getThreadMXBean();
            return threads.isCurrentThreadCpuTimeSupported() ? threads.getCurrentThreadCpuTime() : -1;
        } catch (UnsupportedOperationException e) {
            return -1;
        }
    }

    public static int defaultWorkers() {
        return Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
    }

    @Override
    public String id() {
        return "hda:" + workers;
    }

    @Override
    public String displayName() {
        return "HDA* (" + workers + " worker)";
    }

    @Override
    public String description() {
        return "A* song song: trạng thái được phân cho worker theo hash, trao đổi qua hàng đợi tin nhắn.";
    }

    @Override
    public AlgorithmProperties properties() {
        return properties;
    }

    @Override
    public SearchResult solve(PuzzleProblem problem, Heuristic heuristic, SearchBudget budget, SearchObserver observer) {
        SearchResult early = SearchContext.precheck(this, problem, heuristic);
        if (early != null) return early;
        SearchContext ctx = new SearchContext(this, problem, heuristic, budget, observer);
        AtomicInteger threadId = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "hda-worker-" + threadId.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        try {
            return new Run(ctx, problem.goal(), heuristic, workers, pool).execute();
        } finally {
            pool.shutdownNow();
        }
    }

    /** Tin nhắn: một successor gửi tới worker sở hữu. */
    private record Message(Board board, int g, Node parent, Move move) {
    }

    private static final class Node implements Paths.Step {
        final Board board;
        final Node parent;
        final Move move;
        final int g;
        final int h;
        final long seq;
        boolean closed;
        boolean stale;

        Node(Board board, Node parent, Move move, int g, int h, long seq) {
            this.board = board;
            this.parent = parent;
            this.move = move;
            this.g = g;
            this.h = h;
            this.seq = seq;
        }

        int f() {
            return g + h;
        }

        @Override
        public Paths.Step parent() {
            return parent;
        }

        @Override
        public Move move() {
            return move;
        }
    }

    private static final class Run {
        private final SearchContext ctx;
        private final Goal goal;
        private final Heuristic heuristic;
        private final Worker[] ws;
        private final ExecutorService pool;
        private final Object incumbentLock = new Object();
        private volatile int incumbent = INF;
        private Node bestGoal;

        Run(SearchContext ctx, Goal goal, Heuristic heuristic, int n, ExecutorService pool) {
            this.ctx = ctx;
            this.goal = goal;
            this.heuristic = heuristic;
            this.pool = pool;
            this.ws = new Worker[n];
            for (int i = 0; i < n; i++) ws[i] = new Worker(i);
        }

        int owner(Board b) {
            int h = b.hashCode();
            h ^= (h >>> 16);
            h *= 0x45d9f3b;
            h ^= (h >>> 16);
            return Math.floorMod(h, ws.length);
        }

        void offerGoal(Node n) {
            synchronized (incumbentLock) {
                if (n.g < incumbent) {
                    incumbent = n.g;
                    bestGoal = n;
                }
            }
        }

        SearchResult execute() {
            Board start = ctx.start();
            ws[owner(start)].inbox.add(new Message(start, 0, null, null));
            SearchMetrics m = ctx.metrics();
            m.workers = ws.length;
            List<Callable<Void>> rounds = new ArrayList<>(ws.length);
            for (Worker w : ws) rounds.add(w);
            while (true) {
                if (ctx.shouldStopNow()) return finish(ctx.stoppedResult());
                try {
                    for (Future<Void> f : pool.invokeAll(rounds)) f.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    ctx.shouldStopNow();
                    return finish(ctx.stoppedResult());
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Worker HDA* lỗi", e.getCause());
                }
                m.iterations++;
                aggregate(m);
                ctx.reportProgress();
                if (ctx.memoryExceeded(m.maxClosed + liveOpen())) return finish(ctx.stoppedResult());

                boolean inFlight = false, anyOpen = false;
                int minF = INF;
                for (Worker w : ws) {
                    if (!w.inbox.isEmpty()) inFlight = true;
                    int f = w.minF();
                    if (f < INF) anyOpen = true;
                    minF = Math.min(minF, f);
                }
                if (!inFlight && incumbent < INF && minF >= incumbent) {
                    return finish(ctx.solved(Paths.movesTo(bestGoal)));
                }
                if (!inFlight && !anyOpen) {
                    return finish(ctx.failed(SearchStatus.NO_SOLUTION, "Không tồn tại lời giải cho trạng thái hiện tại."));
                }
            }
        }

        private long liveOpen() {
            long s = 0;
            for (Worker w : ws) s += w.open.size();
            return s;
        }

        private void aggregate(SearchMetrics m) {
            long expanded = 0, generated = 0, dup = 0, reopened = 0, calls = 0, msgs = 0, closed = 0, maxExp = 0;
            for (Worker w : ws) {
                expanded += w.expanded;
                generated += w.generated;
                dup += w.duplicates;
                reopened += w.reopened;
                calls += w.heuristicCalls;
                msgs += w.messages;
                closed += w.closedCount;
                maxExp = Math.max(maxExp, w.expanded);
            }
            m.expanded = expanded;
            m.generated = generated;
            m.duplicates = dup;
            m.reopened = reopened;
            m.heuristicCalls = calls;
            m.messages = msgs;
            m.maxClosed = Math.max(m.maxClosed, closed);
            m.maxOpen = Math.max(m.maxOpen, liveOpen());
            double mean = (double) expanded / ws.length;
            m.loadImbalancePct = mean == 0 ? 100 : Math.round(100.0 * maxExp / mean);
        }

        private SearchResult finish(SearchResult r) {
            SearchMetrics m = ctx.metrics();
            aggregate(m);
            // cpuTimeNs của context chỉ tính luồng điều phối; cộng thêm CPU của mọi worker.
            long workerCpu = 0;
            boolean supported = true;
            for (Worker w : ws) {
                if (w.cpuNanos < 0) supported = false;
                workerCpu += Math.max(0, w.cpuNanos);
            }
            if (supported && m.cpuTimeNs >= 0) m.cpuTimeNs += workerCpu;
            return r;
        }

        /** Một worker: sở hữu các trạng thái có owner = id; chỉ luồng của nó chạm vào OPEN/CLOSED. */
        private final class Worker implements Callable<Void> {
            final int id;
            final ConcurrentLinkedQueue<Message> inbox = new ConcurrentLinkedQueue<>();
            final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(Node::f)
                    .thenComparingInt((Node n) -> n.h).thenComparingLong(n -> n.seq));
            final Map<Board, Node> best = new HashMap<>();
            long seq, expanded, generated, duplicates, reopened, heuristicCalls, messages, closedCount;
            long cpuNanos;

            Worker(int id) {
                this.id = id;
            }

            @Override
            public Void call() {
                long cpuStart = cpuNow();
                try {
                    round();
                } finally {
                    long cpuEnd = cpuNow();
                    if (cpuStart < 0 || cpuEnd < 0) cpuNanos = -1;
                    else if (cpuNanos >= 0) cpuNanos += cpuEnd - cpuStart;
                }
                return null;
            }

            private void round() {
                Message msg;
                while ((msg = inbox.poll()) != null) receive(msg);
                for (int i = 0; i < BATCH; i++) {
                    Node n = open.peek();
                    if (n == null) break;
                    if (n.stale) {
                        open.poll();
                        continue;
                    }
                    if (n.f() >= incumbent) break; // không thể cải thiện lời giải đã có
                    open.poll();
                    n.closed = true;
                    closedCount++;
                    expanded++;
                    if (goal.isGoal(n.board)) continue;
                    for (int d = 0; d < Move.COUNT; d++) {
                        Move m = Move.byOrdinal(d);
                        if (!n.board.canMove(m) || (n.move != null && m == n.move.opposite())) continue;
                        Board child = n.board.move(m);
                        generated++;
                        Message out = new Message(child, n.g + 1, n, m);
                        int o = owner(child);
                        if (o == id) {
                            receive(out);
                        } else {
                            ws[o].inbox.add(out);
                            messages++;
                        }
                    }
                }
            }

            void receive(Message msg) {
                Node existing = best.get(msg.board());
                if (existing != null && msg.g() >= existing.g) {
                    duplicates++;
                    return;
                }
                int h;
                if (existing != null) {
                    h = existing.h;
                    existing.stale = true;
                    if (existing.closed) {
                        reopened++;
                        closedCount--;
                    }
                } else {
                    h = heuristic.estimate(msg.board(), goal);
                    heuristicCalls++;
                }
                Node node = new Node(msg.board(), msg.parent(), msg.move(), msg.g(), h, seq++);
                best.put(msg.board(), node);
                open.add(node);
                if (goal.isGoal(msg.board())) offerGoal(node);
            }

            /** f nhỏ nhất còn sống trong OPEN (chỉ gọi khi worker đang nghỉ giữa các vòng). */
            int minF() {
                while (!open.isEmpty() && open.peek().stale) open.poll();
                return open.isEmpty() ? INF : open.peek().f();
            }
        }
    }
}
