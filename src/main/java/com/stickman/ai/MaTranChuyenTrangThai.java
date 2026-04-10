package com.stickman.ai;

import com.stickman.model.NhanVat;
import com.stickman.model.CauHinhAI;
import com.stickman.model.VatCan;
import java.util.List;

/**
 * MaTranChuyenTrangThai — Ma trận chuyển trạng thái hành vi AI
 * ═══════════════════════════════════════════════════════════════
 * Chức năng : Quản lý chuyển đổi giữa 4 trạng thái chiến thuật
 *             của bot theo điều kiện hiện tại.
 * Thuật toán: Finite State Machine (FSM) với ma trận chuyển:
 *
 *   PATROL → ALERT  : khi phát hiện mục tiêu (khoảng cách < 400)
 *   ALERT  → COMBAT : khi có LOS đến mục tiêu
 *   COMBAT → RETREAT: khi HP < 25% và không có item HP gần
 *   RETREAT→ COMBAT : khi HP > 50% lại
 *   * → PATROL      : khi mục tiêu mất dạng quá 5s
 *
 * Ma trận chuyển (P=xác suất/điều kiện):
 *   Từ\Đến  | PATROL | ALERT | COMBAT | RETREAT
 *   PATROL  |   -    | d<400 |   -    |    -
 *   ALERT   | lost5s |   -   | hasLOS |    -
 *   COMBAT  | lost5s |   -   |   -    | hp<25%
 *   RETREAT |   -    |   -   | hp>50% |    -
 *
 * Input     : bot, target, vatCans, cauHinh, now
 * Output    : TrangThaiHanhVi (enum)
 * Khi gọi  : CayQuyetDinh mỗi frame (khi dungMaTranChuyenTT = true)
 * Ảnh hưởng: Thay đổi trạng thái ảnh hưởng tốc độ và hành vi bot
 * ═══════════════════════════════════════════════════════════════
 */
public class MaTranChuyenTrangThai {

    public enum TrangThaiHanhVi {
        PATROL,  // tuần tra — không thấy địch
        ALERT,   // cảnh giác — phát hiện địch nhưng chưa LOS
        COMBAT,  // chiến đấu — có LOS
        RETREAT  // rút lui — HP thấp
    }

    private static final double DIST_PHAT_HIEN = 400;
    private static final long   THOI_GIAN_MAT  = 5000; // ms mất dạng → PATROL
    private static final double HP_RUT_LUI      = 0.25;
    private static final double HP_QUAY_LAI     = 0.50;

    private TrangThaiHanhVi trangThai = TrangThaiHanhVi.PATROL;
    private long tgianMatDang = 0;

    /**
     * capNhat — Tính toán chuyển trạng thái mới
     * @return TrangThaiHanhVi mới (có thể giống cũ)
     */
    public TrangThaiHanhVi capNhat(NhanVat bot, NhanVat target,
                                    List<VatCan> vatCans, long now) {
        double dist = Math.hypot(bot.getX()-target.getX(), bot.getY()-target.getY());
        boolean hasLOS = kiemTraTamNhin(bot, target, vatCans);
        double hpRatio = (double)bot.getHp() / bot.getMaxHp();

        if (!hasLOS && dist > DIST_PHAT_HIEN) {
            if (tgianMatDang == 0) tgianMatDang = now;
        } else {
            tgianMatDang = 0;
        }

        TrangThaiHanhVi moi = trangThai;
        switch (trangThai) {
            case PATROL -> {
                if (dist < DIST_PHAT_HIEN) moi = TrangThaiHanhVi.ALERT;
            }
            case ALERT -> {
                if (tgianMatDang > 0 && now - tgianMatDang > THOI_GIAN_MAT)
                    moi = TrangThaiHanhVi.PATROL;
                else if (hasLOS)
                    moi = TrangThaiHanhVi.COMBAT;
            }
            case COMBAT -> {
                if (tgianMatDang > 0 && now - tgianMatDang > THOI_GIAN_MAT)
                    moi = TrangThaiHanhVi.PATROL;
                else if (hpRatio < HP_RUT_LUI)
                    moi = TrangThaiHanhVi.RETREAT;
            }
            case RETREAT -> {
                if (hpRatio > HP_QUAY_LAI)
                    moi = TrangThaiHanhVi.COMBAT;
            }
        }

        trangThai = moi;
        return moi;
    }

    public TrangThaiHanhVi layTrangThai() { return trangThai; }

    public void datLai() {
        trangThai   = TrangThaiHanhVi.PATROL;
        tgianMatDang = 0;
    }

    /** heSoTocDo — Hệ số tốc độ theo trạng thái */
    public double heSoTocDo() {
        return switch (trangThai) {
            case PATROL  -> 0.6;
            case ALERT   -> 0.85;
            case COMBAT  -> 1.0;
            case RETREAT -> 1.15;  // chạy nhanh khi rút
        };
    }

    private boolean kiemTraTamNhin(NhanVat a, NhanVat b, List<VatCan> vatCans) {
        double ax=a.getX()+20, ay=a.getY()+20;
        double bx=b.getX()+20, by=b.getY()+20;
        double dx=bx-ax, dy=by-ay, len=Math.hypot(dx,dy);
        if (len<1) return true;
        int steps=Math.min(14,(int)Math.ceil(len/35));
        for (int i=1;i<steps;i++) {
            double t=(double)i/steps;
            double cx=ax+dx*t, cy=ay+dy*t;
            for (VatCan o:vatCans)
                if (cx>=o.getX()&&cx<=o.getX()+o.getRong()&&
                    cy>=o.getY()&&cy<=o.getY()+o.getCao()) return false;
        }
        return true;
    }
}
