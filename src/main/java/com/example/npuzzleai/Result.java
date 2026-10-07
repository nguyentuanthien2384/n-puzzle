package com.example.npuzzleai;

public class Result {
    public String heuristic;
    public int approved;
    public int total;
    public long time;
    public int step;
    public String error;

    public Result(String heuristic, int approved, int total, int step, long time, String error) {
        this.heuristic = heuristic;
        this.approved = approved;
        this.total = total;
        this.step = step;
        this.time = time;
        this.error = error;
    }

    public String showResult() {
        String rs = "Heuristic: " + heuristic + "\n";
        if (error == null) {
            rs += "Số node đã duyệt: " + approved + "\n"
                    + "Tổng số trạng thái sinh ra: " + total + "\n"
                    + "Số bước đi đến đích: " + step + "\n"
                    + "Thời gian tìm kiếm: " + time + "ms\n";
        } else {
            rs += "Không tìm được lời giải\nNguyên nhân: " + error + "\n";
        }
        return rs;
    }
}
