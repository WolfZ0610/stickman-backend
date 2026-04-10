package com.stickman.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/** VatCan — Chướng ngại vật hình chữ nhật trên bản đồ */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class VatCan {
    private double x, y, rong, cao;
}
