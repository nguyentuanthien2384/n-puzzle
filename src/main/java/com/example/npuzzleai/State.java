package com.example.npuzzleai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Vector;

/**
 * Trạng thái của bảng N-Puzzle.
 *
 * <p>Giá trị 0 đại diện cho ô trống. Lớp giữ nguyên hai kiểu trạng thái đích
 * của phiên bản gốc để tương thích với giao diện và báo cáo.</p>
 */
public class State {
    public static int heuristic = 1;
    public static int goal = 1;

    public int[] value;
    private final int size;
    private final int length;
    private int blank;

    public State(int size) {
        if (size < 2) {
            throw new IllegalArgumentException("Kích thước N-Puzzle phải >= 2");
        }
        this.size = size;
        this.length = size * size;
        this.value = new int[length];
        this.blank = 0;
    }

    public State(int[] value, int size) {
        Objects.requireNonNull(value, "value");
        if (size < 2 || value.length != size * size) {
            throw new IllegalArgumentException("Trạng thái không khớp với kích thước bảng");
        }
        this.size = size;
        this.length = size * size;
        this.value = value.clone();
        validatePermutation(this.value);
        this.blank = posBlank(this.value);
    }

    private void validatePermutation(int[] values) {
        boolean[] seen = new boolean[length];
        for (int v : values) {
            if (v < 0 || v >= length || seen[v]) {
                throw new IllegalArgumentException("Trạng thái phải chứa đúng các giá trị từ 0 đến " + (length - 1));
            }
            seen[v] = true;
        }
    }

    public int getSize() {
        return size;
    }

    public int getLength() {
        return length;
    }

    /** Khởi tạo trạng thái đích theo lựa chọn hiện tại. */
    public void Init() {
        if (goal == 1) {
            for (int i = 0; i < length; i++) {
                value[i] = i;
            }
        } else {
            for (int i = 0; i < length - 1; i++) {
                value[i] = i + 1;
            }
            value[length - 1] = 0;
        }
        blank = posBlank(value);
    }

    public int[] createGoalArray() {
        Init();
        return value;
    }

    /**
     * Tạo trạng thái ngẫu nhiên bằng cách đi ngẫu nhiên từ trạng thái đích.
     * Cách này đảm bảo trạng thái sinh ra luôn giải được.
     */
    public int[] createRandomArray() {
        Init();
        Random random = new Random();
        int moves = 20 * size;
        int previousBlank = -1;

        for (int step = 0; step < moves; step++) {
            int currentBlank = posBlank(value);
            List<Integer> candidates = new ArrayList<>(4);
            if (currentBlank / size > 0) candidates.add(currentBlank - size);
            if (currentBlank / size < size - 1) candidates.add(currentBlank + size);
            if (currentBlank % size > 0) candidates.add(currentBlank - 1);
            if (currentBlank % size < size - 1) candidates.add(currentBlank + 1);

            // Tránh đi ngược ngay bước trước nếu còn lựa chọn khác.
            if (candidates.size() > 1) {
                candidates.remove(Integer.valueOf(previousBlank));
            }
            int nextBlank = candidates.get(random.nextInt(candidates.size()));
            swap(currentBlank, nextBlank);
            previousBlank = currentBlank;
        }

        // Hiếm khi random walk quay lại đúng đích; trộn thêm một bước hợp lệ.
        State target = new State(size);
        target.createGoalArray();
        if (Arrays.equals(value, target.value)) {
            int currentBlank = posBlank(value);
            int nextBlank = currentBlank / size < size - 1 ? currentBlank + size : currentBlank - size;
            swap(currentBlank, nextBlank);
        }
        return value;
    }

    private void swap(int a, int b) {
        int temp = value[a];
        value[a] = value[b];
        value[b] = temp;
        blank = b;
    }

    public int posBlank(int[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == 0) return i;
        }
        return -1;
    }

    public boolean isGoal(State goalState) {
        return goalState != null && Arrays.equals(value, goalState.value);
    }

    /**
     * Kiểm tra hai trạng thái có cùng lớp khả giải hay không.
     * Hỗ trợ cả hai kiểu trạng thái đích của ứng dụng.
     */
    public boolean isSolvable(State goalState) {
        if (goalState == null || goalState.size != size) return false;
        int thisInvariant = solvabilityInvariant(value);
        int goalInvariant = solvabilityInvariant(goalState.value);
        return thisInvariant == goalInvariant;
    }

    private int solvabilityInvariant(int[] values) {
        int inversions = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == 0) continue;
            for (int j = i + 1; j < values.length; j++) {
                if (values[j] != 0 && values[i] > values[j]) inversions++;
            }
        }
        if (size % 2 == 1) {
            return inversions & 1;
        }
        int blankRowFromBottom = size - (posBlank(values) / size);
        return (inversions + blankRowFromBottom) & 1;
    }

    public int estimate(State goalState) {
        return switch (heuristic) {
            case 1 -> heuristic1(goalState);
            case 2 -> heuristic2(goalState);
            case 3 -> heuristic3(goalState);
            case 4 -> heuristic4(goalState);
            case 5 -> heuristic5(goalState);
            case 6 -> heuristic6(goalState);
            case 7 -> heuristic7(goalState);
            case 8 -> heuristic8(goalState);
            case 9 -> heuristic9(goalState);
            default -> heuristic2(goalState);
        };
    }

    /** Kiểm tra heuristic hiện tại có hỗ trợ kích thước bảng hay không; trả về thông báo lỗi nếu không. */
    public static String heuristicSizeError(int size) {
        if (heuristic == 9 && size > 4) {
            return "Heuristic H9 Pattern Database chỉ hỗ trợ bảng 3x3 và 4x4.";
        }
        if (heuristic == 7 || heuristic == 8) {
            // Bảng Walking Distance của 5x5 có hơn 8 triệu trạng thái,
            // quá nặng để dựng và giữ trong bộ nhớ ứng dụng.
            if (size > 4) {
                return "Heuristic H" + heuristic + " (Walking Distance) chỉ hỗ trợ bảng 3x3 và 4x4.";
            }
        }
        return null;
    }

    /**
     * Tạo State bỏ qua bước kiểm tra hoán vị - dùng trong vòng lặp tìm kiếm tốc độ cao,
     * khi mảng được sinh ra từ các nước đi hợp lệ nên luôn là hoán vị đúng.
     */
    public static State unchecked(int size, int[] value) {
        State state = new State(size);
        state.value = value;
        return state;
    }

    // Heuristic 1 - Tổng số ô sai vị trí.
    public int heuristic1(State goalState) {
        int[] goalValue = goalState.value;
        int distance = 0;
        for (int i = 0; i < length; i++) {
            if (value[i] != 0 && value[i] != goalValue[i]) distance++;
        }
        return distance;
    }

    private int[] goalPositions(State goalState) {
        // Cache theo nội dung đích: A*/IDA* tra vị trí đích hàng triệu lần/node con.
        if (cachedGoalPos != null && Arrays.equals(cachedGoalKey, goalState.value)) {
            return cachedGoalPos;
        }
        int[] pos = new int[length];
        for (int i = 0; i < length; i++) {
            pos[goalState.value[i]] = i;
        }
        cachedGoalKey = goalState.value.clone();
        cachedGoalPos = pos;
        return pos;
    }

    private static int[] cachedGoalKey;
    private static int[] cachedGoalPos;

    // Heuristic 2 - Tổng khoảng cách Manhattan.
    public int heuristic2(State goalState) {
        int[] goalPos = goalPositions(goalState);
        int distance = 0;
        for (int i = 0; i < length; i++) {
            int tile = value[i];
            if (tile == 0) continue;
            int gi = goalPos[tile];
            distance += Math.abs(gi / size - i / size) + Math.abs(gi % size - i % size);
        }
        return distance;
    }

    // Heuristic 3 - Tổng phần nguyên khoảng cách Euclid.
    public int heuristic3(State goalState) {
        int[] goalPos = goalPositions(goalState);
        int distance = 0;
        for (int i = 0; i < length; i++) {
            int tile = value[i];
            if (tile == 0) continue;
            int gi = goalPos[tile];
            int width = Math.abs(gi % size - i % size);
            int height = Math.abs(gi / size - i / size);
            distance += (int) Math.sqrt(width * width + height * height);
        }
        return distance;
    }

    // Heuristic 4 - Tổng số ô sai hàng và số ô sai cột.
    public int heuristic4(State goalState) {
        int[] goalPos = goalPositions(goalState);
        int distance = 0;
        for (int i = 0; i < length; i++) {
            int tile = value[i];
            if (tile == 0) continue;
            int gi = goalPos[tile];
            if (gi / size != i / size) distance++;
            if (gi % size != i % size) distance++;
        }
        return distance;
    }

    /**
     * Heuristic 5 - Manhattan + xung đột tuyến tính.
     * Cách đếm tương đương ý tưởng trong báo cáo nhưng dùng vị trí đích thực tế,
     * vì vậy hoạt động đúng với cả goal 1 và goal 2.
     */
    public int heuristic5(State goalState) {
        int[] goalPos = goalPositions(goalState);
        int distance = heuristic2(goalState);

        for (int row = 0; row < size; row++) {
            int maxGoalCol = -1;
            for (int col = 0; col < size; col++) {
                int tile = value[row * size + col];
                if (tile == 0) continue;
                int gi = goalPos[tile];
                if (gi / size == row) {
                    int goalCol = gi % size;
                    if (goalCol > maxGoalCol) maxGoalCol = goalCol;
                    else distance += 2;
                }
            }
        }

        for (int col = 0; col < size; col++) {
            int maxGoalRow = -1;
            for (int row = 0; row < size; row++) {
                int tile = value[row * size + col];
                if (tile == 0) continue;
                int gi = goalPos[tile];
                if (gi % size == col) {
                    int goalRow = gi / size;
                    if (goalRow > maxGoalRow) maxGoalRow = goalRow;
                    else distance += 2;
                }
            }
        }
        return distance;
    }

    // Heuristic 6 - Heuristic 5 + số ô bị chặn tại vị trí đích (theo báo cáo gốc).
    public int heuristic6(State goalState) {
        int[] goalValue = goalState.value;
        int[] goalPos = goalPositions(goalState);
        int distance = heuristic5(goalState);

        for (int i = 0; i < length; i++) {
            int tile = value[i];
            if (tile == 0) continue;
            int gi = goalPos[tile];
            if (i == gi) continue;

            int block = 0;
            int count = 0;
            if (gi / size != 0) {
                count++;
                block += value[gi - size] == goalValue[gi - size] ? 1 : 0;
            }
            if (gi / size != size - 1) {
                count++;
                block += value[gi + size] == goalValue[gi + size] ? 1 : 0;
            }
            if (gi % size != 0) {
                count++;
                block += value[gi - 1] == goalValue[gi - 1] ? 1 : 0;
            }
            if (gi % size != size - 1) {
                count++;
                block += value[gi + 1] == goalValue[gi + 1] ? 1 : 0;
            }
            if (count >= 2 && count == block) distance++;
        }
        return distance;
    }

    /**
     * Heuristic 7 - Walking Distance (Takahashi).
     * Thành phần dọc + thành phần ngang, tra bảng dựng trước bằng BFS
     * trên không gian ma trận đếm. Chấp nhận được và trội hơn Manhattan.
     */
    public int heuristic7(State goalState) {
        return WalkingDistance.walkingDistance(this, goalState);
    }

    /**
     * Heuristic 8 - max(Manhattan + xung đột tuyến tính chuẩn, Walking Distance).
     * Xung đột tuyến tính bản chuẩn (Hansson-Mayer-Yung) đếm bằng độ dài dãy con
     * tăng dài nhất (LIS): số ô tối thiểu phải nhấc khỏi hàng/cột để hết xung đột.
     * Lấy max của hai heuristic chấp nhận được vẫn là heuristic chấp nhận được.
     */
    public int heuristic8(State goalState) {
        return Math.max(heuristic2(goalState) + linearConflictExact(goalState), heuristic7(goalState));
    }

    /**
     * Heuristic 9 - Pattern Database rời rạc (Korf-Felner).
     * 3x3: bảng đầy đủ 9! hoán vị (chính xác); 4x4: phân hoạch 5-5-5.
     */
    public int heuristic9(State goalState) {
        return PatternDatabase.lookup(this, goalState);
    }

    /** Tổng xung đột tuyến tính bản chuẩn trên các hàng và cột, nhân đôi như lý thuyết. */
    public int linearConflictExact(State goalState) {
        int[] goalPos = goalPositions(goalState);
        int conflicts = 0;
        for (int row = 0; row < size; row++) {
            int k = 0;
            int[] seq = new int[size];
            for (int col = 0; col < size; col++) {
                int tile = value[row * size + col];
                if (tile == 0) continue;
                int gi = goalPos[tile];
                if (gi / size == row) seq[k++] = gi % size;
            }
            conflicts += k - lisLength(seq, k);
        }
        for (int col = 0; col < size; col++) {
            int k = 0;
            int[] seq = new int[size];
            for (int row = 0; row < size; row++) {
                int tile = value[row * size + col];
                if (tile == 0) continue;
                int gi = goalPos[tile];
                if (gi % size == col) seq[k++] = gi / size;
            }
            conflicts += k - lisLength(seq, k);
        }
        return 2 * conflicts;
    }

    /** Độ dài dãy con tăng dài nhất (strict LIS) bằng kỹ thuật tails, O(k log k). */
    private static int lisLength(int[] seq, int k) {
        int[] tails = new int[k];
        int len = 0;
        for (int i = 0; i < k; i++) {
            int lo = 0, hi = len;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (tails[mid] < seq[i]) lo = mid + 1;
                else hi = mid;
            }
            tails[lo] = seq[i];
            if (lo == len) len++;
        }
        return len;
    }

    public Vector<State> successors() {
        Vector<State> states = new Vector<>();
        int blankPos = posBlank(value);
        if (blankPos / size > 0) addSuccessor(blankPos, blankPos - size, states, value);
        if (blankPos / size < size - 1) addSuccessor(blankPos, blankPos + size, states, value);
        if (blankPos % size > 0) addSuccessor(blankPos, blankPos - 1, states, value);
        if (blankPos % size < size - 1) addSuccessor(blankPos, blankPos + 1, states, value);
        return states;
    }

    public void addSuccessor(int oldBlank, int newBlank, Vector<State> states, int[] oldVal) {
        int[] newVal = oldVal.clone();
        newVal[oldBlank] = newVal[newBlank];
        newVal[newBlank] = 0;
        states.add(new State(newVal, size));
    }

    public void UP() {
        blank = posBlank(value);
        if (blank >= size) swap(blank, blank - size);
    }

    public void RIGHT() {
        blank = posBlank(value);
        if (blank % size != size - 1) swap(blank, blank + 1);
    }

    public void DOWN() {
        blank = posBlank(value);
        if (blank < length - size) swap(blank, blank + size);
    }

    public void LEFT() {
        blank = posBlank(value);
        if (blank % size != 0) swap(blank, blank - 1);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof State other)) return false;
        return size == other.size && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return 31 * size + Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return Arrays.toString(value);
    }
}
