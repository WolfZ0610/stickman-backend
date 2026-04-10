package com.stickman.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * NhanVat — Model nhân vật (player / bot)
 * ═══════════════════════════════════════
 * Dùng cho cả player người và AI bot.
 * AI engine đọc/ghi các trường này mỗi frame.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NhanVat {
    // ── Định danh ──
    private int    id;
    private String tenNhanVat;   // warrior / assassin / tank / sniper
    private String tenVuKhi;     // smg / rifle / pistol / cannon
    private boolean laBot;

    // ── Vị trí & hướng ──
    private double x, y;
    private int    huongX, huongY;   // -1, 0, 1
    private double gocNhin;          // radian, hướng chuột

    // ── Thống số chiến đấu ──
    private int    hp, maxHp;
    private int    giap;
    private double tocDo;
    private double heSoDam;          // bullet damage multiplier

    // ── Vũ khí / đạn ──
    private int    soVienTrong;      // đạn trong băng
    private int    soVienDu;         // đạn dự trữ
    private int    soVienToiDa;      // băng tối đa
    private boolean dangNap;
    private long   tgianNapXong;     // timestamp khi nạp xong (ms)
    private long   cdXong;           // cooldown bắn, timestamp

    // ── Trạng thái AI ──
    private String trangThaiAI;      // ENGAGE / CHASE / COVER / HEAL
}
