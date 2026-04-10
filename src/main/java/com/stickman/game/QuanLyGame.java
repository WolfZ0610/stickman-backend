package com.stickman.game;

import com.stickman.ai.AStar;
import com.stickman.model.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuanLyGame — Quản lý vòng đời game, state các phòng
 * ════════════════════════════════════════════════════════════
 * Chức năng : Lưu trữ và cập nhật trạng thái từng phòng game.
 *             Xử lý physics: di chuyển, va chạm đạn, pickup item.
 *             Gọi QuanLyAI mỗi frame cho chế độ vs AI.
 * Khi gọi  : GameWebSocketHandler nhận message → gọi tickGame()
 * ════════════════════════════════════════════════════════════
 */
@Slf4j
@Component
public class QuanLyGame {

    private static final int CANVAS_W=1100, CANVAS_H=650, TS=40;
    private static final double VIEN_DAN_SPD=8.0, VIEN_DAN_SPD_AI=5.2;

    @Data
    public static class PhongGame {
        private String        roomId;
        private List<NhanVat> nhanVats  = new ArrayList<>();
        private List<Dan>     vienDans  = new ArrayList<>();
        private List<VatCan>  vatCans   = new ArrayList<>();
        private List<ItemGame>items     = new ArrayList<>();
        private CauHinhAI     cauHinhAI;
        private boolean       dangChay;
        private boolean       laVsAI;
        private boolean[][]   walkGrid;
        private long          thoiGianBatDau;
        private long          lastTick;
    }

    private final Map<String, PhongGame> phongGames = new ConcurrentHashMap<>();
    private final QuanLyAI   quanLyAI;
    private final QuanLyBanDo quanLyBanDo;

    public QuanLyGame(QuanLyAI quanLyAI, QuanLyBanDo quanLyBanDo) {
        this.quanLyAI    = quanLyAI;
        this.quanLyBanDo = quanLyBanDo;
    }

    /** taoPhong — Khởi tạo phòng game mới */
    public PhongGame taoPhong(String roomId, CauHinhAI cfg, boolean laVsAI) {
        PhongGame p = new PhongGame();
        p.setRoomId(roomId);
        p.setCauHinhAI(cfg);
        p.setLaVsAI(laVsAI);
        p.setDangChay(false);
        p.setThoiGianBatDau(System.currentTimeMillis());
        p.setLastTick(System.currentTimeMillis());

        // Build bản đồ
        List<VatCan> map = quanLyBanDo.xayDungBanDo(cfg);
        p.setVatCans(map);
        p.setWalkGrid(quanLyBanDo.xayDungWalkGrid(map, CANVAS_W, CANVAS_H, TS));

        // Spawn items
        p.setItems(quanLyBanDo.xuatHienItems(map, 3, 4));

        // AI setup
        quanLyAI.khoiTao(roomId);
        quanLyAI.datWalkGrid(roomId, p.getWalkGrid(),
            (int)Math.ceil((double)CANVAS_W/AStar.GRID_CELL),
            (int)Math.ceil((double)CANVAS_H/AStar.GRID_CELL));

        phongGames.put(roomId, p);
        return p;
    }

    /** batDauGame — Đặt players và bắt đầu tick */
    public void batDauGame(String roomId, NhanVat p1, NhanVat p2) {
        PhongGame p = phongGames.get(roomId);
        if (p == null) return;
        p.getNhanVats().clear();
        p1.setX(80); p1.setY(80);
        p2.setX(970); p2.setY(540);

        // Áp HP bonus cho AI
        int bonus = p.getCauHinhAI().getBonusHP();
        if (bonus > 0 && p.isLaVsAI()) {
            p2.setHp(p2.getHp() + bonus);
            p2.setMaxHp(p2.getMaxHp() + bonus);
        }
        p.getNhanVats().add(p1);
        p.getNhanVats().add(p2);
        p.setDangChay(true);
    }

    /**
     * tickGame — Cập nhật 1 frame game (~16ms)
     * Bao gồm: physics đạn, va chạm, AI tick, pickup items
     * @return snapshot kết quả (để WS gửi về frontend)
     */
    public Map<String, Object> tickGame(String roomId) {
        PhongGame p = phongGames.get(roomId);
        if (p == null || !p.isDangChay()) return Map.of();

        long now = System.currentTimeMillis();
        double dt = Math.min(now - p.getLastTick(), 50); // clamp 50ms
        p.setLastTick(now);

        List<NhanVat> nhanVats = p.getNhanVats();
        List<Dan>     vienDans = p.getVienDans();
        List<VatCan>  vatCans  = p.getVatCans();
        List<ItemGame>items    = p.getItems();

        NhanVat player = nhanVats.isEmpty() ? null : nhanVats.get(0);
        NhanVat bot    = nhanVats.size() > 1 ? nhanVats.get(1) : null;

        // ── Tick AI (vs AI mode) ──
        if (p.isLaVsAI() && bot != null && player != null) {
            quanLyAI.capNhat(roomId, bot, player, p.getCauHinhAI(),
                    vatCans, vienDans, items, now);
        }

        // ── Cập nhật đạn ──
        Iterator<Dan> it = vienDans.iterator();
        List<Map<String,Object>> hits = new ArrayList<>();
        while (it.hasNext()) {
            Dan b = it.next();
            b.setX(b.getX() + b.getVx());
            b.setY(b.getY() + b.getVy());

            // Ra khỏi bản đồ
            if (b.getX()<0||b.getX()>CANVAS_W||b.getY()<0||b.getY()>CANVAS_H) { it.remove(); continue; }

            // Va chạm tường
            boolean hitWall = vatCans.stream().anyMatch(o ->
                b.getX()>=o.getX()&&b.getX()<=o.getX()+o.getRong()&&
                b.getY()>=o.getY()&&b.getY()<=o.getY()+o.getCao());
            if (hitWall) { it.remove(); continue; }

            // Va chạm nhân vật
            for (NhanVat nv : nhanVats) {
                if (nv.getId() == b.getChuSoHuu() || nv.getHp() <= 0) continue;
                if (vaChạmDan(b, nv)) {
                    int satThuong = tinhSatThuong(b.getSatThuong(), nv.getGiap());
                    nv.setHp(Math.max(0, nv.getHp() - satThuong));
                    hits.add(Map.of("targetId", nv.getId(), "satThuong", satThuong, "hp", nv.getHp()));
                    it.remove();
                    break;
                }
            }
        }

        // ── Pickup items ──
        for (NhanVat nv : nhanVats) {
            items.removeIf(item -> {
                if (Math.hypot(nv.getX()-item.getX(), nv.getY()-item.getY()) < 26) {
                    if ("HP".equals(item.getLoai()))   nv.setHp(Math.min(nv.getMaxHp(), nv.getHp()+30));
                    if ("AMMO".equals(item.getLoai())) nv.setSoVienDu(Math.min(nv.getSoVienDu()+20, 80));
                    return true;
                }
                return false;
            });
        }

        // ── Cooldown & reload ──
        for (NhanVat nv : nhanVats) {
            if (nv.isDangNap() && now >= nv.getTgianNapXong()) {
                int need = nv.getSoVienToiDa() - nv.getSoVienTrong();
                int load = Math.min(need, nv.getSoVienDu());
                nv.setSoVienTrong(nv.getSoVienTrong() + load);
                nv.setSoVienDu(nv.getSoVienDu() - load);
                nv.setDangNap(false);
            }
        }

        // ── Kiểm tra kết thúc ──
        boolean p1Dead = player != null && player.getHp() <= 0;
        boolean p2Dead = bot    != null && bot.getHp()    <= 0;

        // ── Build snapshot ──
        Map<String, Object> snap = new HashMap<>();
        snap.put("nhanVats", nhanVats.stream().map(nv -> Map.of(
            "id", nv.getId(), "x", nv.getX(), "y", nv.getY(),
            "hp", nv.getHp(), "maxHp", nv.getMaxHp(),
            "soVienTrong", nv.getSoVienTrong(), "dangNap", nv.isDangNap(),
            "trangThaiAI", nv.getTrangThaiAI() != null ? nv.getTrangThaiAI() : ""))
            .toList());
        snap.put("hits", hits);
        snap.put("ketThuc", p1Dead || p2Dead);
        snap.put("nguThang", p1Dead ? 2 : p2Dead ? 1 : 0);
        if (p.isLaVsAI()) {
            snap.put("aiConsole", quanLyAI.laySnapshotConsole(roomId, bot != null ? bot : new NhanVat()));
        }
        return snap;
    }

    /** batDan — Bot bắn đạn */
    public Dan batDan(String roomId, NhanVat shooter, double aimX, double aimY, boolean laAI) {
        PhongGame p = phongGames.get(roomId);
        if (p == null) return null;
        double cx=shooter.getX()+TS/2.0, cy=shooter.getY()+TS/2.0;
        double ang=Math.atan2(aimY-cy, aimX-cx);
        double spd = laAI ? VIEN_DAN_SPD_AI : VIEN_DAN_SPD;
        Dan b = Dan.builder()
            .x(cx).y(cy)
            .vx(Math.cos(ang)*spd).vy(Math.sin(ang)*spd)
            .chuSoHuu(shooter.getId())
            .satThuong(15).kichThuoc(4)
            .build();
        p.getVienDans().add(b);
        return b;
    }

    /** layPhong — Lấy phòng theo roomId */
    public PhongGame layPhong(String roomId) { return phongGames.get(roomId); }

    /** xoaPhong — Xóa phòng khi kết thúc */
    public void xoaPhong(String roomId) {
        phongGames.remove(roomId);
        quanLyAI.xoa(roomId);
    }

    private boolean vaChạmDan(Dan b, NhanVat nv) {
        int sz = b.getKichThuoc();
        return b.getX()-sz < nv.getX()+TS && b.getX()+sz > nv.getX() &&
               b.getY()-sz < nv.getY()+TS && b.getY()+sz > nv.getY();
    }

    private int tinhSatThuong(int baseDmg, int giap) {
        if (giap <= 0) return baseDmg;
        int reduced = (int)(baseDmg * (1 - giap / 100.0));
        return Math.max(1, reduced);
    }
}
