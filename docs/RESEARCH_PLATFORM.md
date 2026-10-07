# N-Puzzle Heuristic Search Research & Teaching Platform (v3.0)

Tài liệu này mô tả phần phát triển theo *"Hướng phát triển chuyên sâu cho dự án N-Puzzle Java 17 + Maven + JavaFX"*: biến ứng dụng giải N-Puzzle thành một **phòng thí nghiệm heuristic search** dùng cho giảng dạy, nghiên cứu thực nghiệm và benchmark tái lập.

## 1. Kiến trúc

Thay đổi quan trọng nhất: **search engine độc lập + JavaFX chỉ là một client**. GUI, CLI, benchmark và test đều gọi cùng một implementation.

```text
com.example.npuzzleai
├── core          Board (bất biến), Goal (đích tổng quát), Move, PuzzleProblem,
│                 Solvability (đích tuỳ ý), BoardGenerator (có seed), PermutationRank
├── search        SearchAlgorithm, Heuristic, HeuristicProperties, SearchBudget, SearchResult,
│                 SearchMetrics, SearchObserver, SearchEvent, TraceMode, SearchContext,
│                 ConfigurableAlgorithm / ConfigurableHeuristic (plugin có tham số)
├── algorithms    BFS, A* / A* không reopen / Weighted A* / Greedy, IDA*, IDA*-TT, RBFS, SMA*,
│                 HDA* (song song), MCTS/UCT, AlgorithmRegistry (+ nạp plugin)
├── heuristics    Misplaced, Manhattan, Euclid, Row/Col, Linear Conflict (LIS), Walking Distance
│                 (+ WalkingDistanceTables), max(...), H5/H6 gốc (thử nghiệm), HeuristicRegistry
│   └── pdb       PatternDefinition, PatternIndexer, PatternDatabaseBuilder (0-1 BFS), PatternDatabase,
│                 PdbMetadata (checksum), PdbStore (cache RAM + file .pdb), DefaultPartitions,
│                 AdditivePatternDatabaseHeuristic
├── verify        ExactDistanceTable (h* bằng BFS ngược), HeuristicVerifier, HeuristicReport
├── learning      FeatureExtractor, Mlp, LearnedModel, HeuristicTrainer, LearnedHeuristic, FocalSearch
│                 (tự đăng ký qua ServiceLoader như một plugin)
├── benchmark     Dataset/Datasets, ExperimentConfig, ExperimentRunner, RunRecord, SummaryRow,
│                 Statistics, ExperimentExporter, EnvironmentInfo, Json, SvgBarChart
├── research      AdversarialGenerator (sinh puzzle đối kháng bằng tiến hoá)
├── cli           NPuzzleCli
├── ui            Search Lab: SolveTab, CompareTab, VerifierTab, ExperimentTab, AdversarialTab,
│                 LearningTab, BoardView, TraceRecorder
└── (gói gốc)     Ứng dụng JavaFX và các lớp thuật toán v2 (giữ nguyên để tương thích)
```

Phụ thuộc giữa các gói **một chiều, không có vòng**:

```text
core ← search ← {algorithms, heuristics(.pdb), verify} ← learning ← benchmark ← research ← {cli, ui}
```

Gói `learning` cần solver để gán nhãn và cung cấp heuristic/thuật toán mới. Nó không được gói `heuristics`/`algorithms` gọi trực tiếp mà **tự đăng ký qua ServiceLoader** (`provides ... with ...` trong `module-info.java` + `META-INF/services`) — cùng cơ chế với plugin JAR bên ngoài. Các gói vì vậy tách được thành Maven module (`npuzzle-core`, `npuzzle-search-api`, `npuzzle-algorithms`, `npuzzle-heuristics`, `npuzzle-learning`, `npuzzle-benchmark`, `npuzzle-ui`) mà không phải sửa code.

### Search API

```java
public interface SearchAlgorithm {
    String id();                       // "astar", "wastar:2.0", "sma:50000", "hda:4", "focal:1.5"
    AlgorithmProperties properties();  // dùng heuristic? tối ưu? bộ nhớ tuyến tính?
    SearchResult solve(PuzzleProblem problem, Heuristic heuristic,
                       SearchBudget budget, SearchObserver observer);
}

public interface Heuristic {
    String id();
    int estimate(Board board, Goal goal);
    default HeuristicProperties properties() { return HeuristicProperties.unknown(); }
    default void prepare(Goal goal) {}   // dựng/nạp PDB, mô hình - đo riêng, không tính vào thời gian tìm
}
```

- `SearchBudget(timeoutMillis, maxExpandedNodes, maxMemoryBytes)` — 0 là không giới hạn.
- `SearchResult` gồm trạng thái (`SOLVED`, `UNSOLVABLE`, `TIMEOUT`, `NODE_LIMIT`, `MEMORY_LIMIT`, `CANCELLED`...), chuỗi nước đi và `SearchMetrics`.
- `SearchContext` gom instrumentation để **mọi thuật toán được đo theo cùng một định nghĩa**: `expanded, generated, duplicates, reopened, pruned, maxOpen, maxClosed, heuristicCalls, heuristicTimeNs, iterations, regenerated, recursiveCalls, maxDepth, fLimitUpdates, evictions, messages, workers, loadImbalancePct, solutionLength, wallTimeNs, cpuTimeNs` (+ `peakHeapBytes, gcCount, gcTimeNs, preprocessTimeNs` do benchmark điền).
- `SearchObserver.traceMode()`: `OFF` (benchmark — không tạo sự kiện), `SAMPLED` (1/K node), `FULL_TRACE` (mọi node, chỉ cho 3x3).

## 2. Thuật toán

| Mã | Thuật toán | Tối ưu? | Ghi chú |
|---|---|---|---|
| `bfs` | BFS | ✔ | Baseline mù, tới 4x4 |
| `astar` | A* | ✔ (h admissible) | Reopen + tie-break cấu hình được (`AStarSearch.Config`) |
| `astar-noreopen` | A* không reopen | ✔ nếu h nhất quán | Để thí nghiệm chính sách reopen |
| `wastar:w` | Weighted A*, f = g + w·h | ≤ w × tối ưu | "Fast solve mode" |
| `greedy` | Greedy Best-First | ✘ | |
| `ida` | IDA* (Korf 1985) | ✔ | `iterations`, `regenerated` = node mở rộng ngoài vòng cuối |
| `ida-tt:MB` | IDA* + bảng chuyển vị | ✔ | Bảng ánh xạ trực tiếp theo MB, khoá nén chính xác (không va chạm giả) |
| `rbfs` | RBFS (Korf 1993) | ✔ | `recursiveCalls`, `maxDepth`, `fLimitUpdates`, `regenerated` |
| `sma:N` | SMA* (Russell 1992) | ✔ nếu N > độ sâu lời giải | Ngân sách node tường minh hoặc suy từ `maxMemoryBytes`; `evictions` |
| `hda:N` | HDA* (Kishimoto–Fukunaga–Botea 2009) | ✔ | N worker trên đa nhân, trạng thái phân theo hash, trao đổi qua hàng đợi; `messages`, `loadImbalancePct` |
| `focal:w[:h_F]` | Focal Search (Pearl & Kim 1982) | ≤ w × tối ưu | OPEN theo f với h admissible, FOCAL sắp theo h_F (mặc định mạng nơ-ron) |
| `mcts:N` | MCTS/UCT (Kocsis & Szepesvári 2006) | ✘ | N mô phỏng mỗi nước, rollout ε-greedy; baseline so sánh paradigm |

Các thuật toán v2 (Value Iteration, Hill Climbing, Simulated Annealing, GA, Bidirectional BFS) vẫn chạy được ở màn hình chính.

**HDA\*.** Mỗi trạng thái thuộc về đúng một worker theo hash. Khi sinh successor, worker gửi nó (kèm g và con trỏ cha) vào hàng đợi của worker sở hữu. Các worker chạy theo vòng đồng bộ: nhận tin, mở rộng tối đa 128 node, rồi rào chắn. Thuật toán dừng khi không còn tin đang truyền và f nhỏ nhất của mọi OPEN ≥ chi phí lời giải tốt nhất — điều kiện này bảo đảm tối ưu (có reopen nên không cần h nhất quán). Số node mở rộng có thể dao động nhẹ giữa các lần chạy vì thứ tự nhận tin phụ thuộc lập lịch luồng; độ dài lời giải thì không. `cpuTimeNs` cộng CPU của mọi worker. Đây là proof-of-concept trong một tiến trình; phân tán qua nhiều máy cần thêm tầng mạng.

**MCTS.** N-Puzzle cổ điển là bài toán đường đi ngắn nhất tất định với cận dưới mạnh, nên MCTS chỉ đóng vai baseline giảng dạy (độ dài lời giải dài hơn tối ưu, số mô phỏng lớn). Kết quả tất định: seed suy từ trạng thái xuất phát và số mô phỏng. Đường đi được cắt vòng lặp trước khi trả về.

## 3. Heuristic và kiểm định

| Mã | Heuristic | Khai báo |
|---|---|---|
| `zero` | h = 0 | A, C |
| `misplaced` | H1 số ô sai vị trí | A, C |
| `manhattan` | H2 Manhattan | A, C |
| `euclid` | H3 Euclid (phần nguyên) | A, C |
| `rowcol` | H4 sai hàng + sai cột | A, C |
| `linear-conflict` | Manhattan + Linear Conflict chuẩn (đếm bằng LIS) | A, C |
| `legacy-h5` | H5 gốc (running-max) | **thử nghiệm** |
| `legacy-h6` | H6 gốc (H5 + ô bị chặn) | **thử nghiệm** |
| `walking-distance` | H7 Walking Distance (3x3, 4x4) | A, C |
| `wd-lc` | H8 max(MD+LC, WD) | A, C |
| `apdb` | H9 Additive PDB, phân hoạch mặc định (3x3 chính xác, 4x4 5-5-5, 5x5 6×4) | A |
| `apdb:<partition>` | PDB cộng tường minh, ví dụ `apdb:4x4@1,2,5,6,9;3,4,7,8,11;10,12,13,14,15` | A |
| `pdb:<ô...>` | PDB một pattern, ví dụ `pdb:4x4@1,2,3,4,5` | A |
| `learned[:file]` | Mạng nơ-ron (MLP) học từ lời giải tối ưu | **thử nghiệm**, không admissible |

**Khai báo không phải bằng chứng.** `HeuristicVerifier` dựng h* cho toàn bộ 181.440 trạng thái 3x3 bằng BFS ngược rồi kiểm tra:

- h(s) ≤ h*(s) trên mọi trạng thái (admissibility), kèm phản ví dụ nếu vi phạm;
- h(s) ≤ 1 + h(s') trên mọi cạnh (consistency);
- độ chính xác: tỉ lệ h = h*, trung bình/trung vị (h* − h), trung bình h theo từng h*, mức trội giữa các heuristic.

`HeuristicPropertiesTest` chạy kiểm định này cho mọi heuristic × 3 đích (chuẩn, ô trống trước, tuỳ ý) trong mỗi lần `mvn test`.

### Kết quả kiểm định trên 3x3 (đích chuẩn, `cli verify`)

| Heuristic | h > h\* | Cạnh vi phạm nhất quán | h = h\* | TB h | TB (h\* − h) |
|---|---|---|---|---|---|
| misplaced | 0 | 0 | 0,04% | 7,11 | 14,86 |
| manhattan | 0 | 0 | 1,30% | 14,00 | 7,97 |
| linear-conflict | 0 | 0 | 1,61% | 15,12 | 6,85 |
| **legacy-h5** | 0 | **360** | 1,62% | 15,13 | 6,84 |
| **legacy-h6** | 0 | **1.530** | 1,62% | 15,18 | 6,79 |
| walking-distance | 0 | 0 | 3,19% | 15,68 | 6,29 |
| wd-lc | 0 | 0 | 3,38% | 16,03 | 5,94 |
| apdb (PDB đầy đủ) | 0 | 0 | 100% | 21,97 | 0 |

Kết luận cho báo cáo: trên toàn bộ không gian 3x3, **H5/H6 của báo cáo gốc vẫn admissible nhưng không nhất quán**. A* không reopen vì vậy có thể mất tính tối ưu với H5/H6. Admissibility trên 4x4 chưa được chứng minh: cách đếm running-max có thể cộng +4 cho thứ tự đích `[2, 0, 1]` trong khi chỉ cần nhấc một ô (+2). Hai heuristic này giữ nhãn thử nghiệm.

### Pattern Database

- Mô hình chi phí "pattern-moves" (Korf–Felner): chỉ tính nước đi của ô thuộc pattern, nên các pattern **rời nhau** cộng được mà vẫn admissible. Pattern chồng nhau bị từ chối khi tạo heuristic.
- Bảng đã lấy min theo vị trí ô trống nên có thể không nhất quán (trừ PDB đầy đủ 3x3). A* vì vậy mặc định có reopen; IDA\*, RBFS, SMA\* không bị ảnh hưởng.
- File `.pdb` tự mô tả: kích thước, trạng thái đích, ô pattern, mô hình chi phí, phiên bản builder, số entry, CRC32. Khi nạp, metadata phải khớp đích/pattern và checksum phải đúng.
- Cache đĩa mặc định ở `pdb-cache/` (đổi bằng `-Dnpuzzle.pdb.dir=...`, tắt bằng `none`). Lần đầu dựng: 4x4 5-5-5 khoảng 5 giây; 5x5 6×4 khoảng 10–30 giây; các lần sau nạp từ đĩa trong vài chục mili giây.

## 4. Nghiên cứu nâng cao

### Learned heuristic + Focal Search

Pipeline: *trạng thái đã giải → (đặc trưng, khoảng cách còn lại) → mạng nơ-ron → LearnedHeuristic → Focal Search → so sánh với PDB*.

- **Đặc trưng** (tương đối theo `Goal`): Manhattan, Linear Conflict, số ô sai vị trí/sai hàng/sai cột, Walking Distance, khoảng cách ô trống, xung đột góc, Manhattan²/n. Với 4x4 có thể thêm tổng additive PDB (`BASIC_PDB`).
- **Nhãn**: 3x3 dùng h\* chính xác; 4x4 giải tối ưu các bài đi ngẫu nhiên bằng IDA\* + PDB, và mỗi trạng thái trên đường đi tối ưu là một mẫu (còn lại = độ dài − vị trí).
- **Mô hình**: MLP một lớp ẩn ReLU, Adam, viết bằng Java thuần nên solver vẫn JVM-native, không cần Python. Lưu dạng văn bản (`*.model`), nhỏ, đưa vào repo được.
- **Học phần dư + cân bằng theo h\***: mạng học h\* − Manhattan (kẹp ≥ 0, vì Manhattan luôn là cận dưới). Với 3x3, tập train được cân bằng sao cho mỗi mức h\* có số mẫu ngang nhau (lặp lại ở các mức ít trạng thái). Lấy mẫu đều chỉ cho vài chục trạng thái gần đích, khiến mạng dự đoán ~6,6 cho trạng thái có h\* = 3. Sau khi cân bằng, đường hiệu chỉnh bám sát: h\* = 3 → 3,0; h\* = 6 → 6,3; h\* = 30 → 27,8.
- **Kết quả 3x3** (`cli train-heuristic --size 3`): MAE ≈ 2,3 bước trên tập kiểm định lấy đều, chính xác hơn nhiều so với Manhattan (sai số TB 7,97) và Linear Conflict (6,85). Đổi lại, mạng đánh giá vượt h\* ở khoảng 43% trạng thái.
- **Dùng an toàn**: không đưa h học được vào A\* rồi gọi kết quả là tối ưu. `FocalSearch` giữ OPEN theo f = g + h với **h admissible đã kiểm định** (ví dụ PDB). Mạng nơ-ron chỉ quyết định node nào trong FOCAL (f ≤ w·f_min) được mở rộng trước, nên độ dài vẫn ≤ w × tối ưu — đúng hướng "admissible h = PDB, learned h = ordering signal".

```bash
cli train-heuristic --size 4 --instances 500 --out models/learned-4x4.model
cli solve --random 4 --seed 3 --algo focal:2 --heuristic apdb      # focal đọc models/learned-4x4.model nếu có
cli verify --heuristics manhattan,learned                         # đo mức vượt h* của mạng trên 3x3
```

Repo kèm sẵn hai mô hình đã huấn luyện, được nạp tự động khi chạy từ thư mục dự án:

- `models/learned-3x3.model`: nhãn h\* chính xác, cân bằng theo h\*; MAE 2,30; vượt h\* ở 43,4% trạng thái.
- `models/learned-4x4.model`: 400 lời giải tối ưu, đặc trưng có PDB; MAE 1,18; vượt h\* ở 30,3% trạng thái.

Nếu không có file mô hình, `learned` tự huấn luyện khi chuẩn bị (3x3 khoảng 1 giây; 4x4 giải 150 bài để lấy nhãn, vài giây). Focal Search chạy bước này **trước** khi bắt đầu đo thời gian.

### Sinh puzzle đối kháng

`AdversarialGenerator` dùng tiến hoá (μ+λ) với chọn lọc giải đấu. Đột biến luôn giữ khả giải: đi ngẫu nhiên 1..k nước, hoặc hai phép đổi chỗ ô số rời nhau (parity không đổi). Có hai mục tiêu:

- `gap`: tìm trạng thái có h\* − h lớn nhất, tức nơi heuristic đánh giá thấp nhất. Ví dụ: với Linear Conflict trên 3x3, tìm được trạng thái h = 13 trong khi h\* = 27.
- `expansions`: tìm trạng thái khiến một solver mở nhiều node nhất (có trần ngân sách).

Kết quả xuất được thành dataset để benchmark, minh hoạ rằng benchmark trung bình là chưa đủ:

```bash
cli adversarial --size 4 --objective expansions --algo ida --heuristic linear-conflict \
                --population 30 --generations 20 --top 10 --out adversarial-15p.txt
cli benchmark --dataset file:adversarial-15p.txt --algos ida,rbfs --heuristics linear-conflict,apdb
```

## 5. CLI

```bash
# Windows: cli.bat <lệnh>     Linux/macOS: ./cli.sh <lệnh>
# hoặc trực tiếp: mvn -q compile exec:java -Dexec.args="<lệnh>"

cli list                                                   # thuật toán, heuristic, dataset
cli solve --board 1,2,3,4,5,6,0,7,8 --algo ida --heuristic linear-conflict
cli solve --random 4 --steps 60 --seed 7 --algo hda:4 --heuristic apdb --show-path
cli verify [--goal blank-first] [--heuristics a,b] [--csv verify.csv]
cli benchmark --dataset random-15p --algos astar,ida,rbfs,hda:4,focal:2,wastar:2 \
              --heuristics linear-conflict,apdb --reps 3 --timeout 60 --max-mem-mb 1024
cli dataset --name hard-15p --seed 20261006 --out hard-15p.txt
cli build-pdb --size 4 --partition "1,2,5,6,9;3,4,7,8,11;10,12,13,14,15"
cli train-heuristic --size 3|4 [--instances 300] [--hidden 16] [--epochs 25] [--out models/...]
cli adversarial --size 3|4 --objective gap|expansions [--algo ...] [--heuristic ...] [--out file.txt]
cli plugins --dir plugins
```

Trên Windows nên viết bảng bằng dấu phẩy (`1,2,3,...`) để tránh lỗi dấu ngoặc kép khi truyền qua Maven.

## 6. Thí nghiệm tái lập

Dataset dựng sẵn (sinh có seed): `easy-3x3`, `exact-3x3`, `custom-goal-3x3`, `unsolvable-3x3`, `random-15p`, `hard-15p`, `unsolvable-15p`, `random-24p`. Có thể nạp file riêng bằng `--dataset file:đường_dẫn`, ví dụ dataset đối kháng.

Quy trình của `ExperimentRunner`:

1. Lọc tổ hợp hợp lệ (thuật toán không dùng heuristic chỉ chạy một lần với `none`).
2. Tiền xử lý heuristic (dựng/nạp PDB) — đo riêng thành `preprocessingMs`.
3. Warm-up (không ghi) để JIT biên dịch code nóng.
4. Chạy mọi tổ hợp × instance × lần lặp, **xáo thứ tự theo seed**, trên executor riêng với số luồng cố định (không dùng `ForkJoinPool.commonPool()`).
5. Kiểm chứng độc lập: đường đi phải tới đích; instance vô nghiệm phải bị báo vô nghiệm; tổ hợp có cam kết tối ưu phải cho đúng độ dài tham chiếu. Độ dài tham chiếu là **h\* chính xác** với 3x3. Với 4x4 trở lên, đó là **đồng thuận**: độ dài ngắn nhất mà các tổ hợp có cam kết tối ưu tìm được. Nhờ vậy vẫn phát hiện được lỗi tối ưu khi không có oracle, và tính được optimality gap cho W-A\*/Focal.

Thư mục kết quả:

```text
experiments/2026-10-07-153000-ten/
├── manifest.json       cấu hình, dataset checksum, PDB checksum, seed, Git commit, JDK, JVM args,
│                       nguồn độ dài tham chiếu (exact/consensus), số lỗi tính đúng đắn
├── environment.json    OS, CPU, RAM, JDK vendor/version, JVM args, phiên bản dependency Maven
├── puzzles.json        instance + độ dài tối ưu đã biết
├── dataset.txt         dataset dạng văn bản (nạp lại bằng --dataset file:...)
├── raw-results.csv     từng lần chạy với đầy đủ metrics
├── summary.csv         trung vị, p10/p90, độ lệch chuẩn, tỉ lệ giải, tỉ lệ tối ưu, optimality gap
├── charts/*.svg        node mở rộng, thời gian, tỉ lệ giải, maxOpen
└── logs/run.log
```

### Ví dụ: `random-15p` (40 bảng 4x4, một lần chạy, một máy — chỉ để minh hoạ)

Intel Core thế hệ 7, JDK 21, timeout 10 s, độ dài tham chiếu từ kiểm tra đồng thuận (0 lỗi tính đúng đắn).

| Tổ hợp | Giải | Median expanded | Median ms | Độ dài TB | Gap TB |
|---|:---:|---:|---:|---:|---:|
| A\* + linear-conflict | 40/40 | 13.585 | 26,8 | 37,20 | 1,00 |
| A\* + apdb | 40/40 | 4.146 | 4,8 | 37,20 | 1,00 |
| IDA\* + linear-conflict | 40/40 | 46.804 | 47,0 | 37,20 | 1,00 |
| IDA\* + apdb | 40/40 | 7.050 | 2,8 | 37,20 | 1,00 |
| IDA\*-TT 64 MB + apdb | 40/40 | 5.846 | 10,0 | 37,20 | 1,00 |
| RBFS + apdb | 40/40 | 10.418 | 3,2 | 37,20 | 1,00 |
| SMA\* 500k node + linear-conflict | 38/40 | 19.002 | 112,9 | 36,84 | 1,00 |
| SMA\* 500k node + apdb | 40/40 | 6.614 | 24,1 | 37,20 | 1,00 |
| HDA\* 4 worker + apdb | 40/40 | 9.250 | 10,9 | 37,20 | 1,00 |
| W-A\* (w=2) + apdb | 40/40 | 418 | 0,5 | 44,25 | 1,19 |
| Focal (w=2, thứ tự mạng nơ-ron) + apdb | 40/40 | 256 | 2,3 | 54,25 | 1,46 |
| MCTS 200 mô phỏng + apdb | 40/40 | 400 | 8,4 | 60,05 | 1,62 |

Mọi tổ hợp tối ưu cho cùng độ dài trung bình — kiểm tra đồng thuận đạt. Bảng cũng minh hoạ đúng nhận định của tài liệu gốc: giảm số node không đồng nghĩa nhanh hơn (IDA\*-TT mở ít node hơn IDA\* nhưng chậm hơn do chi phí bảng). Song song hoá một lần tìm kiếm (HDA\*) trên bài toán nhỏ cũng không bù được chi phí đồng bộ. SMA\* với heuristic yếu (Linear Conflict) hết 10 giây ở 2/40 bài vì phải sinh lại các nhánh đã bị loại khỏi bộ nhớ.

Lưu ý phương pháp luận:

- Heap đỉnh và GC chỉ đo khi `threads = 1` (chạy song song thì heap là tài nguyên chung) — `manifest.json` ghi `memoryMetricsValid`.
- `heuristicTimeNs` là ước lượng (đo 1/32 số lần gọi rồi nhân lại) để việc đo không làm sai lệch thời gian; HDA\* không đo chỉ số này.
- Báo cáo nên dùng trung vị và phân phối thay vì chỉ trung bình; giữ `raw-results.csv` cùng báo cáo.
- `maxMemoryBytes` là ngân sách **ước lượng** theo số node đang giữ; để giới hạn RAM thật hãy đặt `-Xmx` (ví dụ `MAVEN_OPTS=-Xmx2g`).

### Microbenchmark (JMH)

Theo khuyến nghị của OpenJDK, JMH nằm trong dự án Maven độc lập `benchmarks-jmh/` (đo `manhattan`, `linearConflict`, `additivePdb`, sinh successor, tra tập đóng, khoá nén):

```bash
mvn -DskipTests install
cd benchmarks-jmh && mvn -q package && java -jar target/benchmarks.jar -f 2 -wi 3 -i 5
```

Để phân tích GC/heap của một lần chạy chậm, dùng JDK Flight Recorder:
`MAVEN_OPTS="-XX:StartFlightRecording=filename=run.jfr" cli benchmark ...` rồi `jfr summary run.jfr`.

## 7. Search Lab (JavaFX)

Nút **Search Lab (nghiên cứu)** ở màn hình chính mở cửa sổ gồm sáu tab. Mọi tác vụ nặng chạy bằng `javafx.concurrent.Task` nên giao diện không bị treo và có thể dừng.

- **Giải & Replay** — chọn thuật toán/heuristic/tham số (w, số node SMA\*, MB bảng TT, số worker HDA\*, số mô phỏng MCTS), timeout, ngân sách bộ nhớ, chế độ trace. Trong lúc chạy, xem tiến độ trực tiếp (node đang xét với g/h/f). Sau đó phát lại **lời giải** hoặc **quá trình mở rộng node** (◀ ▶ ⏯, tốc độ, timeline). Chế độ **Teaching** tô màu ô theo khoảng cách Manhattan và viền đỏ ô xung đột tuyến tính, giải thích h = MD + LC (và h\* với 3x3). Có thêm **heatmap** vị trí ô trống của các node đã mở rộng.
- **Compare Lab** — chạy nhiều tổ hợp trên cùng bảng/đích/timeout/bộ nhớ; bảng expanded/generated/maxOpen/thời gian, cột "Tối ưu?" và biểu đồ cột (có thang log).
- **Kiểm định heuristic** — kiểm định vét cạn 3x3, phản ví dụ, biểu đồ trung bình h theo h\* so với đường lý tưởng h = h\*.
- **Thí nghiệm** — Experiment Manager: dataset, seed, thuật toán, heuristic, số lần lặp, warm-up, timeout, bộ nhớ, số luồng → thư mục kết quả hoàn chỉnh như CLI.
- **Sinh puzzle khó** — tiến hoá đối kháng, biểu đồ fitness theo thế hệ, bảng ứng viên. Có thể mở ứng viên trong tab Giải hoặc lưu thành dataset.
- **Học heuristic** — huấn luyện mạng nơ-ron (3x3/4x4), báo cáo RMSE/MAE/tỉ lệ vượt h\*, biểu đồ phân tán dự đoán so với h\*, lưu mô hình.

Màn hình chính cũng có thêm **IDA\*-TT, RBFS, SMA\*, W-A\* 1.5** (dùng heuristic H1–H9 đang chọn, qua cùng search engine).

## 8. Plugin

Viết lớp cài đặt `SearchAlgorithm` hoặc `Heuristic` (constructor public không tham số), đóng gói JAR với file `META-INF/services/com.example.npuzzleai.search.SearchAlgorithm` (hoặc `...search.Heuristic`) chứa tên lớp, đặt JAR vào `plugins/` rồi chạy `cli plugins --dir plugins`. Cài đặt `ConfigurableAlgorithm`/`ConfigurableHeuristic` để nhận tham số qua mã `ten:tham_so`. Plugin dùng `SearchContext` sẽ được đo bằng đúng các metrics như thuật toán có sẵn. Gói `learning` chính là một ví dụ plugin nội bộ.

```java
public final class MyHeuristic implements Heuristic {
    public String id() { return "my-h"; }
    public int estimate(Board b, Goal g) { return BasicHeuristics.manhattan(b, g); }
    public HeuristicProperties properties() { return HeuristicProperties.admissibleAndConsistent("..."); }
}
```

## 9. Kiểm thử và CI

161 test, chạy toàn bộ trong khoảng 17 giây:

| Test | Nội dung |
|---|---|
| `core/BoardTest`, `core/SolvabilityTest` | Board bất biến, khoá nén đơn ánh; khả giải khớp BFS vét cạn (2x2, 3x3, đích tuỳ ý) và kiểm tra ngẫu nhiên tới 6x6 |
| `verify/ExactDistanceTableTest` | Oracle: 181.440 trạng thái, đường kính 31; 2x2 đường kính 6 |
| `heuristics/HeuristicPropertiesTest` | Kiểm định vét cạn mọi heuristic × 3 đích; quan hệ trội |
| `heuristics/PatternDatabaseTest` | Chỉ số song ánh, PDB cộng admissible và trội Manhattan, từ chối pattern chồng nhau, file + checksum |
| `algorithms/OptimalityRegressionTest` | Mọi thuật toán tối ưu (kể cả HDA\*) × heuristic admissible = h\* trên golden puzzles 3x3; thống nhất trên 4x4; W-A\* ≤ w × tối ưu; SMA\* loại node mà vẫn tối ưu; ngân sách/hủy/trace |
| `algorithms/ParallelAndMonteCarloTest` | HDA\* tối ưu với 1/2/4/8 worker, trao đổi tin, hủy/giới hạn; MCTS hợp lệ, tất định, cắt vòng lặp |
| `learning/LearningTest` | Mạng nơ-ron chính xác hơn Manhattan, lưu/nạp mô hình, Focal Search giữ cận w × tối ưu |
| `research/AdversarialGeneratorTest` | Fitness không giảm, đột biến giữ khả giải, tất định theo seed, xuất dataset |
| `benchmark/ExperimentRunnerTest`, `cli/NPuzzleCliTest` | Thư mục thí nghiệm đầy đủ, song song = tuần tự, kiểm tra đồng thuận 4x4, dataset tất định, mọi lệnh CLI |

GitHub Actions (`.github/workflows/ci.yml`): mỗi push/PR chạy `mvn verify` (test + JaCoCo), một benchmark nhỏ trên `exact-3x3` (đối chiếu h\*) và một benchmark đồng thuận trên `random-15p`. Benchmark đầy đủ chạy theo lịch đêm hoặc thủ công (`nightly-benchmark.yml`) vì thời gian trên máy CI dùng chung bị nhiễu.

## 10. Đối chiếu lộ trình

| Hạng mục trong tài liệu | Ưu tiên | Trạng thái |
|---|---|---|
| Search/Core API tách khỏi JavaFX | P0 | ✔ Theo package, phụ thuộc một chiều, sẵn sàng tách Maven module |
| Correctness benchmark: reverse BFS 3x3, admissibility/consistency | P0 | ✔ `verify` + test; phát hiện H5/H6 không nhất quán |
| IDA\* (cơ bản + TT) với instrumentation | P0 | ✔ |
| State encoding hiệu năng (khoá nén 64/128 bit) | P0 | ✔ một phần: khoá nén cho IDA\*-TT; Board dùng `byte[]`. Các tối ưu khác chờ số liệu JMH |
| JavaFX async: Task, dừng, tiến độ | P0 | ✔ Search Lab |
| CI + reproducibility | P0 | ✔ GitHub Actions, JaCoCo, manifest, kiểm tra đồng thuận |
| Pattern Database + additive/disjoint PDB | P1 | ✔ Partition cấu hình được, metadata, checksum, file `.pdb` |
| RBFS, SMA\* | P1 | ✔ |
| Compare Lab, Experiment export CSV/JSON + biểu đồ | P1 | ✔ |
| Weighted A\* / bounded-suboptimal (Focal) | — | ✔ |
| Custom goal / kích thước tuỳ ý | P2 | ✔ Goal tổng quát, khả giải tổng quát (2x2–10x10 ở core) |
| Parallel batch benchmark (executor riêng) | P2 | ✔ |
| Learned heuristic + bounded-suboptimal | P2 | ✔ Proof-of-concept: MLP Java + Focal Search |
| HDA\* / parallel search | P2 | ✔ Proof-of-concept đa nhân trong một tiến trình |
| MCTS/UCT baseline | P3 | ✔ |
| Adversarial generator | P3 | ✔ |
| Plugin qua ServiceLoader | — | ✔ |
| Tách Maven multi-module | — | Chưa: package đã phân lớp một chiều nên việc tách chỉ còn là cấu hình build |
| Distributed qua nhiều máy, huấn luyện quy mô DeepCubeA (GPU) | — | Ngoài phạm vi proof-of-concept |
