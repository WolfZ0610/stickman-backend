package com.stickman.model;
import lombok.*;
/** ItemGame — Vật phẩm HP hoặc AMMO trên bản đồ */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ItemGame {
    private String loai;   // "HP" | "AMMO"
    private double x, y;
    private int    kichThuoc;
    private long   id;
}
