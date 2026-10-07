# N-Puzzle-AI

Dự án bài tập lớn môn **Nhập môn Trí tuệ nhân tạo** về bài toán ghép tranh **N-Puzzle**. Ứng dụng được viết bằng **Java 17 + JavaFX**, hỗ trợ chơi thủ công, trộn bảng từ trạng thái đích, nạp ảnh, và tự động giải bằng **9 thuật toán tìm kiếm** từ cơ bản đến chuyên sâu: BFS, A\*, IDA\*, Greedy Best-First, Bidirectional BFS, Value Iteration (MDP), Hill Climbing, Simulated Annealing, Genetic Algorithm, đi kèm **9 hàm heuristic** (đánh giá, Manhattan, Euclid, sai hàng/cột, linear conflict, Walking Distance, Pattern Database).

## Phiên bản 3.0 — Research & Teaching Platform

Từ v3.0, dự án có một **search engine độc lập JavaFX** dùng chung cho giao diện, CLI, benchmark và unit test (chi tiết: [docs/RESEARCH_PLATFORM.md](docs/RESEARCH_PLATFORM.md)):

- **Search API** chuẩn hoá: `Board` bất biến, `Goal` tổng quát (đích tuỳ ý), `SearchAlgorithm`, `Heuristic` có khai báo tính chất, `SearchBudget` (thời gian/node/bộ nhớ), `SearchResult` + `SearchMetrics` đầy đủ (expanded, generated, duplicates, reopened, maxOpen, heuristicCalls, regenerated, evictions...).
- **Thuật toán mới**: Weighted A\*, A\* không reopen, IDA\*-TT (bảng chuyển vị), **RBFS**, **SMA\*** (ngân sách bộ nhớ tường minh).
- **Pattern Database tổng quát**: partition cấu hình được, PDB cộng (disjoint) có kiểm tra rời nhau, metadata + checksum, lưu/nạp file `.pdb`.
- **Kiểm định heuristic vét cạn**: BFS ngược dựng h\* cho toàn bộ 181.440 trạng thái 3x3, kiểm tra h ≤ h\* và tính nhất quán trên mọi cạnh, kèm phản ví dụ. H5/H6 gốc được gắn nhãn *thử nghiệm*.
- **Benchmark tái lập**: dataset có seed + checksum, warm-up, xáo thứ tự, executor riêng, xuất `manifest.json`, `environment.json`, `raw-results.csv`, `summary.csv`, biểu đồ SVG; JMH trong dự án riêng `benchmarks-jmh/`.
- **Search Lab** (nút mới ở màn hình chính): Giải & Replay (phát lại lời giải và quá trình mở rộng node, chế độ Teaching, heatmap), Compare Lab, Kiểm định heuristic, Experiment Manager — chạy nền bằng `javafx.concurrent.Task`.
- **CLI** (`cli.bat` / `cli.sh`) và **CI** GitHub Actions (test + JaCoCo + benchmark nhỏ tất định).

```bash
cli.bat list                                         # Linux/macOS: ./cli.sh list
cli.bat solve --board 1,2,3,4,5,6,0,7,8 --algo ida --heuristic linear-conflict
cli.bat verify                                       # kiểm định vét cạn mọi heuristic trên 3x3
cli.bat benchmark --dataset random-15p --algos astar,ida,rbfs --heuristics manhattan,apdb --reps 3
```

## Chức năng

- Bảng **3x3, 4x4, 5x5** tương ứng Dễ / Trung bình / Khó.
- Hai kiểu trạng thái đích như giao diện và báo cáo gốc.
- Chơi bằng chuột hoặc phím **W/A/S/D**.
- Hiển thị bằng ô số hoặc ảnh do người dùng chọn.
- Trộn bảng bằng các bước di chuyển hợp lệ nên trạng thái sinh ra luôn có lời giải.
- Nút dừng tìm kiếm và giới hạn 60 giây cho mỗi lượt giải.
- Tốc độ phát lại lời giải tự thích ứng (lời giải dài của tìm kiếm cục bộ phát nhanh hơn).
- Menu thuật toán có thêm **IDA\*-TT, RBFS, SMA\*, W-A\* 1.5** (dùng heuristic H1–H9 đang chọn, chạy qua search engine mới).
- Nút **Search Lab (nghiên cứu)** mở phòng thí nghiệm với bảng và đích hiện tại.

## Thuật toán tìm kiếm

| Nhóm | Thuật toán | Đặc điểm | Bảng hỗ trợ |
|---|---|---|---|
| Tìm kiếm không có thông tin | **BFS** | Tối ưu về số bước, tốn bộ nhớ | 3x3 |
| | **Bidirectional BFS** | Hai lượt BFS gặp nhau ở giữa, giảm theo cấp số nhân số trạng thái nhớ | 3x3, 4x4 |
| Tìm kiếm heuristic | **A\*** | Tối ưu với heuristic chấp nhận được | 3x3, 4x4, 5x5 |
| | **IDA\*** (Korf 1985) | A\* bằng sâu lặp theo ngưỡng f = g + h, bộ nhớ chỉ theo độ sâu — giải được 4x4/5x5 | 3x3, 4x4, 5x5 |
| | **Greedy Best-First** | Chỉ theo h, rất nhanh nhưng không tối ưu | 3x3, 4x4, 5x5 |
| Quy hoạch động / MDP | **Value Iteration** | Phương trình Bellman V(s) = 1 + min V(s') tới hội tụ, trích chính sách tham lam tối ưu | 3x3 |
| Tìm kiếm cục bộ | **Hill Climbing** | Leo dốc nhất + đi ngang + bước thoát ngẫu nhiên; không đảm bảo tối ưu | 3x3 |
| | **Simulated Annealing** | Chấp nhận nước đi xấu theo xác suất e^(-Δ/T), nhiệt độ giảm dần | 3x3 |
| | **Genetic Algorithm** | Nhiễm sắc thể = chuỗi nước đi; tuyển chọn giải đấu, lai ghép, đột biến | 3x3 |

IDA\* và Greedy dùng heuristic đang chọn trong menu (nên chọn H7/H8/H9 cho 4x4).

## Heuristic

| H | Tên | Chấp nhận được | Ghi chú |
|---|---|---|---|
| H1 | Số ô sai vị trí | ✔ | Yếu nhất, dùng làm cơ sở so sánh |
| H2 | Manhattan | ✔ | Chuẩn mực thông dụng |
| H3 | Euclid (phần nguyên) | ✔ | |
| H4 | Sai hàng + sai cột | ✔ | |
| H5 | Manhattan + xung đột tuyến tính (quét max) | ~ | Bản xấp xỉ theo báo cáo gốc, hiếm khi đếm dư |
| H6 | H5 + ô bị chặn | ~ | |
| H7 | **Walking Distance** (Takahashi) | ✔ | Tra bảng dựng trước bằng BFS trên không gian ma trận đếm; thường trội Manhattan |
| H8 | max(Manhattan + **LC chuẩn LIS**, Walking Distance) | ✔ | LC chuẩn đếm bằng dãy con tăng dài nhất (Hansson-Mayer-Yung) |
| H9 | **Pattern Database** (Korf-Felner) | ✔ | 3x3: bảng đầy đủ 9! hoán vị — chính xác tuyệt đối; 4x4: phân hoạch 5-5-5 rời rạc, dựng bằng 0-1 BFS (~2-3 giây lần đầu) |

H7/H8/H9 chỉ mở cho bảng 3x3 và 4x4 (bảng Walking Distance của 5x5 có hơn 8 triệu trạng thái, quá nặng cho ứng dụng desktop). So sánh heuristic (nút **SS heuristic**) chạy A\* với H1-H8.

## Các lỗi/điểm yếu của source cũ đã được hoàn thiện

- BFS trước đây chỉ tránh quay lại node cha nên sinh lặp rất nhiều; bản này dùng `HashSet` để đánh dấu trạng thái đã duyệt.
- A\* trước đây có thể thêm trùng node vào FRINGE; bản này dùng `PriorityQueue` + `gScore`/`bestG`.
- Các heuristic 2-6 trước đây dùng `Arrays.binarySearch()` trên trạng thái đích thứ 2 (mảng không được sắp tăng), dẫn đến tính sai. Bản này ánh xạ chính xác vị trí đích của từng ô.
- Thêm kiểm tra **khả giải (solvability)** trước khi tìm kiếm.
- Sửa lỗi phím không phải W/A/S/D làm `value` và `state.value` mất đồng bộ.
- Việc phát lại lời giải cập nhật giao diện qua JavaFX Application Thread thay vì vẽ trực tiếp từ background thread.
- So sánh heuristic giữ lại đường đi của heuristic hiệu quả nhất để có thể bấm **Chạy** sau khi so sánh.
- Hiển thị đúng nội dung lỗi thực tế thay vì luôn báo quá thời gian.
- Cập nhật Maven từ Java/JavaFX bản EA cũ sang cấu hình Java 17 ổn định và bổ sung unit test.

## Yêu cầu môi trường

- **JDK 17** trở lên.
- **Apache Maven 3.8+**.
- IntelliJ IDEA là tùy chọn, không bắt buộc.

Kiểm tra:

```bash
java -version
mvn -version
```

## Chạy dự án

Tại thư mục có `pom.xml`:

```bash
mvn clean javafx:run
```

Trong IntelliJ IDEA, mở trực tiếp thư mục dự án, chờ Maven tải dependency rồi chạy Maven goal `javafx:run` hoặc chạy class `N_PuzzleApplication` với cấu hình Maven/JavaFX phù hợp.

## Chạy kiểm thử

```bash
mvn test
```

Các test chính nằm tại:

```text
src/test/java/com/example/npuzzleai/StateTest.java              (v2)
src/test/java/com/example/npuzzleai/SearchTest.java             (v2)
src/test/java/com/example/npuzzleai/AdvancedSearchTest.java     (v2)
src/test/java/com/example/npuzzleai/core/                       Board, khả giải vét cạn
src/test/java/com/example/npuzzleai/verify/                     oracle h* (181.440 trạng thái, đường kính 31)
src/test/java/com/example/npuzzleai/heuristics/                 kiểm định vét cạn mọi heuristic, PDB
src/test/java/com/example/npuzzleai/algorithms/                 regression tối ưu, ngân sách, hủy, trace
src/test/java/com/example/npuzzleai/benchmark/                  thí nghiệm tái lập, dataset
src/test/java/com/example/npuzzleai/cli/                        CLI
```

Bộ test v2 xác minh: heuristic H7/H8 chấp nhận được và nhất quán, H9 chính xác trên 3x3 và tối ưu trên 4x4, IDA\*/Bidirectional BFS/Value Iteration cho độ dài tối ưu trùng với khoảng cách đúng, đường đi của mọi thuật toán đều là chuỗi nước đi hợp lệ, và các giới hạn kích thước (BFS/Value Iteration/tìm kiếm cục bộ 3x3, H7-H9 tới 4x4).

Bộ test v3 kiểm định vét cạn mọi khai báo admissible/consistent trên toàn bộ không gian 3x3 với ba kiểu đích, đối chiếu mọi thuật toán tối ưu × heuristic chấp nhận được với h\* chính xác, và kiểm tra pipeline thí nghiệm xuất đủ file. `mvn verify` tạo thêm báo cáo coverage tại `target/site/jacoco/`.

## Cấu trúc dự án

```text
n-puzzle-ai-main/
├─ pom.xml
├─ README.md
├─ cli.bat / cli.sh                (v3 - CLI)
├─ .github/workflows/              (v3 - CI và benchmark đêm)
├─ benchmarks-jmh/                 (v3 - microbenchmark JMH, dự án Maven riêng)
├─ docs/
│  ├─ Bao_cao_N_Puzzle_EDITABLE.docx
│  └─ RESEARCH_PLATFORM.md         (v3 - hướng dẫn nền tảng nghiên cứu)
├─ src/
│  ├─ main/java/com/example/npuzzleai/
│  │  ├─ core/        (v3) Board, Goal, Move, Solvability, BoardGenerator
│  │  ├─ search/      (v3) SearchAlgorithm, Heuristic, SearchBudget, SearchResult, SearchMetrics...
│  │  ├─ algorithms/  (v3) BFS, A*/W-A*/Greedy, IDA*, IDA*-TT, RBFS, SMA*, AlgorithmRegistry
│  │  ├─ heuristics/  (v3) heuristic cơ bản, Walking Distance, H5/H6 gốc, pdb/ (PDB tổng quát)
│  │  ├─ verify/      (v3) ExactDistanceTable, HeuristicVerifier
│  │  ├─ benchmark/   (v3) dataset, ExperimentRunner, xuất CSV/JSON/SVG
│  │  ├─ cli/         (v3) NPuzzleCli
│  │  ├─ ui/          (v3) Search Lab
│  │  │  (các lớp v2 bên dưới giữ nguyên)
│  │  ├─ AStar.java            (A* + 6 heuristic gốc)
│  │  ├─ BFS.java
│  │  ├─ IDAStar.java          (mới)
│  │  ├─ GreedyBestFirst.java  (mới)
│  │  ├─ BidirectionalBFS.java (mới)
│  │  ├─ ValueIteration.java   (mới - MDP/Bellman)
│  │  ├─ HillClimbing.java     (mới - tìm kiếm cục bộ)
│  │  ├─ SimulatedAnnealing.java (mới)
│  │  ├─ GeneticAlgorithm.java (mới)
│  │  ├─ WalkingDistance.java  (mới - heuristic H7)
│  │  ├─ PatternDatabase.java  (mới - heuristic H9)
│  │  ├─ Permutation.java      (mới - mã hoá hoán vị/Lehmer)
│  │  ├─ Paths.java            (mới - rút gọn & kiểm tra đường đi)
│  │  ├─ HandleImage.java
│  │  ├─ N_PuzzleApplication.java
│  │  ├─ N_PuzzleController.java
│  │  ├─ Node.java
│  │  ├─ Result.java
│  │  └─ State.java
│  ├─ main/resources/com/example/npuzzleai/
│  │  ├─ game-view.fxml
│  │  ├─ style.css
│  │  └─ img/
│  └─ test/java/com/example/npuzzleai/
│     ├─ AdvancedSearchTest.java (mới)
│     ├─ SearchTest.java
│     └─ StateTest.java
└─ ...
```

## Lưu ý hiệu năng

- BFS tăng bộ nhớ rất nhanh theo độ sâu nên chỉ mở cho **3x3**. Với 4x4/5x5 nên dùng **A\*** hoặc **IDA\***, ưu tiên heuristic H2/H5/H6; trên 4x4 chọn **A\* PDB (H9)** hoặc **IDA\* + H8** sẽ giảm node duyệt xuống hàng trăm lần.
- Lần đầu chạy H7/H8/H9, ứng dụng dựng bảng tra (WD 4x4 ~25.000 trạng thái tức thì; PDB 4x4 gồm 3 pattern × 5,77 triệu trạng thái, khoảng 2-4 giây) rồi cache cho các lượt sau trong phiên chạy.
- Trạng thái do nút **Trộn** tạo ra được sinh từ trạng thái đích bằng chuỗi nước đi hợp lệ nên luôn giải được.
- Hill Climbing / Simulated Annealing / GA là **tìm kiếm cục bộ**: có thể thất bại hoặc cho lời giải dài — đây là hành vi đúng của nhóm thuật toán này, hữu ích để minh hoạ trong báo cáo.

## Báo cáo

Bản Word trong thư mục `docs/` đã được lưu lại thành **DOCX có thể chỉnh sửa**. Nội dung và bố cục trực quan được giữ nguyên từ báo cáo được cung cấp để người dùng có thể sửa tên nhóm, nội dung, hình ảnh hoặc bổ sung phần mô tả source mới.
