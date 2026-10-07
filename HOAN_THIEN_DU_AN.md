# Ghi chú hoàn thiện dự án

Bản này được hoàn thiện từ source và báo cáo được cung cấp, giữ nguyên phạm vi đề tài N-Puzzle: BFS, A* và 6 heuristic. Ở phiên bản 2.0, dự án được mở rộng thêm nhóm thuật toán tìm kiếm chuyên sâu chuẩn giáo trình Trí tuệ nhân tạo (Russell & Norvig; Korf; Korf & Felner; Takahashi).

## Phát triển chuyên sâu (v2.0)

### Heuristic mới (State.java)

1. **H7 - Walking Distance (Takahashi)**: tra bảng dựng trước bằng BFS trên không gian ma trận đếm "ô ở hàng nào cần về hàng đích nào". Thành phần dọc + ngang dùng chung một bảng; bảng phụ thuộc hàng đích của ô trống (đích 1: góc trên trái, đích 2: góc dưới phải) và được cache theo (kích thước, lớp đích ô trống). Chấp nhận được, nhất quán, thường trội Manhattan.
2. **H8 - max(Manhattan + Linear Conflict chuẩn, Walking Distance)**: xung đột tuyến tính bản chuẩn (Hansson-Mayer-Yung) đếm bằng độ dài dãy con tăng dài nhất (LIS) trên từng hàng/cột — số ô tối thiểu phải nhấc ra để hết xung đột, khác với bản quét running-max xấp xỉ của H5.
3. **H9 - Pattern Database (Korf & Felner 2002)**: 3x3 dùng bảng đầy đủ 9! hoán vị (heuristic chính xác = khoảng cách tối ưu); 4x4 dùng phân hoạch 5-5-5 rời rạc, mỗi pattern 5 ô + ô trống (~5,77 triệu trạng thái) dựng bằng 0-1 BFS trong đó đi ô thường chi phí 0, đi ô Pattern chi phí 1; tổng của ba bảng là lower bound chấp nhận được. Bảng cache theo nội dung đích.
4. H7/H8/H9 giới hạn ở bảng 3x3/4x4 (bảng WD của 5x5 vượt 8 triệu trạng thái, quá nặng); A*, IDA*, Greedy kiểm tra `State.heuristicSizeError()` trước khi tìm kiếm.
5. Cache vị trí đích (`goalPositions`) theo nội dung mảng đích để vòng lặp A*/IDA* không cấp phát lại hàng triệu lần.

### Thuật toán tìm kiếm mới

1. **IDAStar.java** - Iterative Deepening A* (Korf 1985): sâu lặp tăng ngưỡng f = g + h, bộ nhớ theo độ sâu, tối ưu khi heuristic chấp nhận được; bỏ nước đi lùi lại bước trước; dùng heuristic đang chọn (H1-H9).
2. **GreedyBestFirst.java** - hàng đợi ưu tiên theo h, có bestG/closed chống lặp; nhanh, không đảm bảo tối ưu.
3. **BidirectionalBFS.java** - hai lượt BFS theo từng mức, luôn mở rộng phía rìa nhỏ hơn; khi gặp nhau chọn điểm gặp có tổng độ dài nhỏ nhất => lời giải tối ưu; giới hạn 1,5 triệu trạng thái.
4. **ValueIteration.java** - mô hình hoá MDP tất định, lặp phương trình Bellman V(s) = 1 + min V(s') tới hội tụ rồi trích chính sách tham lam; dùng mã hoá Lehmer để duyệt 9! trạng thái của 3x3; chỉ mở cho 3x3.
5. **HillClimbing.java** - leo dốc nhất, đi ngang qua bình nguyên (tối đa 40 bước) và bước thoát ngẫu nhiên khi kẹt; ghi nhận đúng bản chất "có thể kẹt cực trị địa phương".
6. **SimulatedAnnealing.java** - chấp nhận nước đi xấu với xác suất e^(-Δ/T), lịch làm nguội T *= 0.9997 từ T0 = 24.
7. **GeneticAlgorithm.java** - nhiễm sắc thể = chuỗi 80 nước đi, fitness = -(1000·Manhattan + số bước hiệu lực); tuyển chọn giải đấu (k=3), ưu tú 12 cá thể, lai ghép một điểm (p=0.9), đột biến 4%/gen.
8. **Permutation.java** - mã hoá/giải mã hoán vị hệ Lehmer (dùng bởi PDB và Value Iteration), mã hoá bộ k vị trí cho bảng Pattern.
9. **WalkingDistance.java** - dựng bảng WD; bảng băm long->byte tự viết (địa chỉ mở) thay HashMap để tiết kiệm bộ nhớ.
10. **PatternDatabase.java** - dựng bảng đầy đủ (3x3) và 0-1 BFS pattern space (4x4) với mã hoá cơ số 16 (4 bit/vị trí).
11. **Paths.java** - rút gọn đường đi xoá cặp nước đi trái chiều (dùng cho kết quả tìm kiếm cục bộ) và kiểm tra bước đi hợp lệ (dùng trong unit test).

### Tích hợp giao diện

1. Menu thuật toán mở rộng: A* H1-H9, IDA*, Greedy, Bi-BFS, Val.Iter, HillClim, SimAnn, GA.
2. Nút Dừng đặt cờ cho mọi thuật toán; mỗi lớp có cờ `stop` riêng và giới hạn 60 giây.
3. Tốc độ phát lại lời giải tự thích ứng theo độ dài (600ms -> 15ms) để lời giải dài của tìm kiếm cục bộ không phải chờ quá lâu.
4. Bảng kết quả hiển thị tên đầy đủ thuật toán + heuristic; so sánh heuristic mở rộng H1-H8.

### Kiểm thử

1. `AdvancedSearchTest.java`: H7/H8 chấp nhận được trên 300 trạng thái ngẫu nhiên cả hai đích (so với bảng BFS độc lập), H9 chính xác 3x3, H9 tối ưu 4x4, IDA* tối ưu H1-H4/H7-H9 trên 3x3 và trùng A* trên 4x4, Bidirectional BFS tối ưu, Value Iteration tối ưu cả hai đích và phát hiện vô nghiệm, ba thuật toán tìm kiếm cục bộ giải được trạng thái gần đích và từ chối 4x4, rút gọn đường đi đúng.
2. Khi phát triển đã phát hiện và sửa ba lỗi: (a) hàm rank Lehmer đếm sai số phần tử nhỏ hơn chưa dùng; (b) bảng WD giả định ô trống đích ở hàng cuối nên sai với đích 1; (c) BFS của PDB cập nhật sai mã hoá khi ô trống đổi chỗ với ô Pattern. Các lỗi này khiến heuristic trả giá trị sai âm thầm — đã được kiểm thử che phủ.

## Ghi chú hoàn thiện (v1.1)

1. BFS có `visited` để loại trạng thái lặp, có giới hạn bộ nhớ và kiểm tra khả giải.
2. A* dùng `PriorityQueue`, `bestG` và cơ chế mở lại node khi tìm được đường tốt hơn.
3. Sửa cách tìm vị trí đích trong heuristic để hoạt động đúng với cả hai trạng thái đích.
4. Trạng thái trộn được tạo bằng nước đi hợp lệ, luôn thuộc lớp trạng thái có lời giải.
5. Sửa lỗi đồng bộ bàn cờ khi nhấn phím ngoài W/A/S/D.
6. Việc phát lại lời giải được cập nhật qua JavaFX Application Thread.
7. So sánh heuristic giữ lại lời giải tốt nhất để có thể phát lại.
8. Bổ sung kiểm thử cho State, BFS, A* và trường hợp vô nghiệm.
9. Cập nhật Maven/JavaFX về cấu hình Java 17 ổn định.
10. Báo cáo Word được đóng gói lại thành bản DOCX có thể chỉnh sửa và giữ nguyên bố cục hiển thị.

## Kiểm tra đã thực hiện

- Các lớp thuật toán `State`, `Node`, `BFS`, `AStar`, `Result` đã được biên dịch bằng JDK 21 trong môi trường kiểm tra.
- Smoke test chạy với cả 2 trạng thái đích; BFS và A* H1-H6 tìm cùng độ dài lời giải trên các trạng thái kiểm tra.
- Kiểm tra trạng thái vô nghiệm hoạt động trước khi tìm kiếm.
- POM và FXML được kiểm tra là XML hợp lệ.
- Báo cáo Word được render đủ 20 trang và so sánh pixel với bản cung cấp: bố cục không thay đổi.

Lưu ý: môi trường kiểm tra không có Maven/JavaFX runtime cài sẵn, nên phần giao diện JavaFX không được khởi chạy trực tiếp tại đây. Dự án đã được giữ đúng cấu trúc Maven và có lệnh chạy `mvn clean javafx:run`.
