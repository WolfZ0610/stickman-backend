package com.stickman.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Dan — Model viên đạn
 * Vận tốc dùng đơn vị px/frame (16ms)
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Dan {
    private double x, y;
    private double vx, vy;       // vận tốc pixel/frame
    private int    chuSoHuu;     // id nhân vật bắn
    private int    satThuong;
    private int    kichThuoc;    // bán kính va chạm
    private boolean laDanPhao;
    private double mucTichLuc;   // 0.0 → 1.0 (cannon charge)
}
