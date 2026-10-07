# N-Puzzle-AI

Dự án bài tập lớn môn **Nhập môn Trí tuệ nhân tạo** về bài toán ghép tranh **N-Puzzle**. Ứng dụng được viết bằng **Java 17 + JavaFX**, hỗ trợ chơi thủ công, trộn bảng từ trạng thái đích, nạp ảnh, và tự động giải bằng **9 thuật toán tìm kiếm** từ cơ bản đến chuyên sâu: BFS, A\*, IDA\*, Greedy Best-First, Bidirectional BFS, Value Iteration (MDP), Hill Climbing, Simulated Annealing, Genetic Algorithm, đi kèm **9 hàm heuristic** (đánh giá, Manhattan, Euclid, sai hàng/cột, linear conflict, Walking Distance, Pattern Database).

## Chức năng

- Bảng **3x3, 4x4, 5x5** tương ứng Dễ / Trung bình / Khó.
- Hai kiểu trạng thái đích như giao diện và báo cáo gốc.
- Chơi bằng chuột hoặc phím **W/A/S/D**.
- Hiển thị bằng ô số hoặc ảnh do người dùng chọn.
- Trộn bảng bằng các bước di chuyển hợp lệ nên trạng thái sinh ra luôn có lời giải.
- Nút dừng tìm kiếm và giới hạn 60 giây cho mỗi lượt giải.
- Tốc độ phát lại lời giải tự thích ứng (lời giải dài của tìm kiếm cục bộ phát nhanh hơn).

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
src/test/java/com/example/npuzzleai/StateTest.java
src/test/java/com/example/npuzzleai/SearchTest.java
src/test/java/com/example/npuzzleai/AdvancedSearchTest.java
```

Bộ test xác minh: heuristic H7/H8 chấp nhận được và nhất quán, H9 chính xác trên 3x3 và tối ưu trên 4x4, IDA\*/Bidirectional BFS/Value Iteration cho độ dài tối ưu trùng với khoảng cách đúng, đường đi của mọi thuật toán đều là chuỗi nước đi hợp lệ, và các giới hạn kích thước (BFS/Value Iteration/tìm kiếm cục bộ 3x3, H7-H9 tới 4x4).

## Cấu trúc dự án

```text
n-puzzle-ai-main/
├─ pom.xml
├─ README.md
├─ docs/
│  └─ Bao_cao_N_Puzzle_EDITABLE.docx
├─ src/
│  ├─ main/java/com/example/npuzzleai/
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
