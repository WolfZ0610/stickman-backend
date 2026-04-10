package com.stickman.game;

import com.stickman.ai.*;
import com.stickman.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * QuanLyAI — Điều phối toàn bộ AI Engine
 * ════════════════════════════════════════════════════════════
 * Chức năng : Khởi tạo và gọi CayQuyetDinh mỗi frame.
 *             Tính toán bắn đạn dự đoán cho bot.
 *             Cung cấp snapshot trạng thái cho frontend (log console).
 * Khi gọi  : GameService.tickGame() mỗi 16ms (60fps)
 * ════════════════════════════════════════════════════════════
 */
@Slf4j
@Component
public class QuanLyAI {

    private static final int CANVAS_W = 1100, CANVAS_H = 650, TS = 40;

    // Map roomId → CayQuyetDinh instance (mỗi phòng có AI riêng)
    private final Map<String, CayQuyetDinh> cayQuyetDinhMap = new HashMap<>();

    /** khoiTao — Tạo AI engine mới cho 1 phòng */
    public void khoiTao(String roomId) {
        cayQuyetDinhMap.put(roomId, new CayQuyetDinh(CANVAS_W, CANVAS_H));
    }

    /** capNhat — Chạy 1 frame AI cho bot trong phòng */
    public CayQuyetDinh.HanhDongAI capNhat(String roomId,
                                             NhanVat bot, NhanVat target, CauHinhAI cfg,
                                             List<VatCan> vatCans, List<Dan> vienDans,
                                             List<ItemGame> items, long now) {
        CayQuyetDinh dt = cayQuyetDinhMap.computeIfAbsent(roomId,
                id -> new CayQuyetDinh(CANVAS_W, CANVAS_H));
        // Đảm bảo walk grid được cập nhật
        if (dt.layInfluenceMap() != null) {
            // (walkGrid được set từ QuanLyBanDo khi build map)
        }
        return dt.xuLy(bot, target, cfg, vatCans, vienDans, items, now);
    }

    /** datWalkGrid — Đặt walk grid cho A* trong phòng */
    public void datWalkGrid(String roomId, boolean[][] grid, int gw, int gh) {
        CayQuyetDinh dt = cayQuyetDinhMap.get(roomId);
        if (dt != null) dt.datWalkGrid(grid, gw, gh);
    }

    /** datLai — Reset AI engine khi game kết thúc */
    public void datLai(String roomId) {
        CayQuyetDinh dt = cayQuyetDinhMap.get(roomId);
        if (dt != null) dt.datLai();
    }

    /** xoa — Xóa AI instance khi phòng đóng */
    public void xoa(String roomId) { cayQuyetDinhMap.remove(roomId); }

    /**
     * tinhDamDuDoan — Predictive aiming: tính điểm player SẼ đến
     * @return {aimX, aimY} vị trí bắn
     */
    public double[] tinhDamDuDoan(NhanVat shooter, NhanVat target) {
        double cx=shooter.getX()+TS/2.0, cy=shooter.getY()+TS/2.0;
        double tx=target.getX()+TS/2.0,  ty=target.getY()+TS/2.0;
        double bspd = 8.0 * 0.65; // HE_SO_DAN_AI
        double pvx  = target.getHuongX() * target.getTocDo();
        double pvy  = target.getHuongY() * target.getTocDo();
        if (Math.abs(pvx)<0.01 && Math.abs(pvy)<0.01) return new double[]{tx, ty};
        // Newton iterative (3 vòng)
        double t = Math.hypot(tx-cx, ty-cy) / Math.max(bspd, 1);
        for (int i=0;i<3;i++) {
            double px2=tx+pvx*t, py2=ty+pvy*t;
            t = Math.hypot(px2-cx, py2-cy) / Math.max(bspd, 1);
        }
        return new double[]{
            Math.max(0, Math.min(CANVAS_W-TS, tx+pvx*t - TS/2.0)),
            Math.max(0, Math.min(CANVAS_H-TS, ty+pvy*t - TS/2.0))
        };
    }

    /** laySnapshotConsole — Lấy dữ liệu cho Tab Console Logic */
    public Map<String, Object> laySnapshotConsole(String roomId, NhanVat bot) {
        CayQuyetDinh dt = cayQuyetDinhMap.get(roomId);
        if (dt == null) return Map.of();
        Map<String, Object> snap = new HashMap<>();
        snap.put("btState",    dt.layBT().layTrangThai().name());
        snap.put("fsmState",   dt.layFSM().layTrangThai().name());
        snap.put("saNhietDo",  dt.laySANhietDo());
        snap.put("imDangerBot",dt.layInfluenceMap().layGiaTri(bot.getX(), bot.getY()));
        snap.put("imData",     dt.layInfluenceMap().layMang());
        snap.put("imW",        dt.layInfluenceMap().layImW());
        snap.put("imH",        dt.layInfluenceMap().layImH());
        return snap;
    }
}
