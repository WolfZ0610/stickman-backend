package com.stickman.ai;

import com.stickman.model.*;
import java.util.List;

/**
 * BehaviorTree — Bộ điều phối hành vi AI (Priority Selector)
 * ═══════════════════════════════════════════════════════════════
 * Chức năng : Điều phối toàn bộ hành vi bot theo cấu trúc Priority
 *             Selector — ưu tiên từ P1 (cao nhất) đến P4 (mặc định).
 * Thuật toán: Priority Selector BT:
 *   P1 HEAL   — HP < 25% VÀ có item HP gần → sprint lấy HP
 *   P2 COVER  — đang reload HOẶC HP < 38% HOẶC danger > 160
 *                 VÀ useCover bật VÀ tìm được điểm nấp
 *   P3 CHASE  — mất LOS VÀ khoảng cách > 115 → A* đuổi theo
 *   P4 ENGAGE — mặc định → tấn công + cơ động chiến thuật
 * MIN_STATE_MS = 280ms: giữ mỗi state tối thiểu để tránh flicker.
 * Input     : bot, target, cauHinh, now, aiSpd, vatCans, items,
 *             influenceMap, coverSystem, astar, hillClimbing, sa
 * Output    : Hành động được thực hiện (bot.huongX/Y thay đổi)
 * Khi gọi  : CayQuyetDinh.xuLy() mỗi frame
 * Ảnh hưởng: Gọi A*, SA, HC, Cover tùy trạng thái
 * ═══════════════════════════════════════════════════════════════
 */
public class BehaviorTree {

    public enum TrangThaiBT { HEAL, COVER, CHASE, ENGAGE }

    private static final long MIN_STATE_MS = 280;

    private TrangThaiBT trangThai = TrangThaiBT.ENGAGE;
    private long        btTimer   = 0;

    /**
     * tick — Chạy 1 frame Behavior Tree
     * @param bot       bot AI
     * @param target    mục tiêu (player)
     * @param cauHinh   cấu hình thuật toán
     * @param now       thời gian ms
     * @param aiSpd     tốc độ AI pixel/frame
     * @param vatCans   danh sách vật cản
     * @param items     danh sách vật phẩm HP/AMMO
     * @param infMap    influence map (null nếu không dùng)
     * @param cover     cover system
     * @param walkGrid  grid đi được (cho A*)
     * @param gw        chiều rộng grid
     * @param gh        chiều cao grid
     * @param sa        simulated annealing instance
     * @param hc        hill climbing (static method)
     * @return TrangThaiBT hiện tại
     */
    public TrangThaiBT tick(NhanVat bot, NhanVat target, CauHinhAI cauHinh,
                             long now, double aiSpd, List<VatCan> vatCans,
                             List<ItemGame> items, InfluenceMap infMap,
                             CoverSystem cover, boolean[][] walkGrid, int gw, int gh,
                             SimulatedAnnealing sa) {
        // Cập nhật IM nếu cần
        // (infMap.capNhat đã được gọi từ CayQuyetDinh trước khi vào đây)

        TrangThaiBT next = chuyenTrangThai(bot, target, cauHinh, now, vatCans, items, infMap, cover);
        boolean hasLOS = coTamNhin(bot, target, vatCans);

        switch (next) {

            case HEAL -> {
                // ── P1: Sprint đến item HP gần nhất ──
                ItemGame hp = timVatPhamGan(bot, items, "HP");
                if (hp == null) { trangThai = TrangThaiBT.ENGAGE; break; }
                AStar.HuongDi dir = cauHinh.isDungAStar()
                    ? AStar.timDuong(bot.getX(), bot.getY(), hp.getX(), hp.getY(), walkGrid, gw, gh)
                    : null;
                bot.setHuongX(dir != null ? dir.dx() : (int)Math.signum(hp.getX()-bot.getX()));
                bot.setHuongY(dir != null ? dir.dy() : (int)Math.signum(hp.getY()-bot.getY()));
                diChuyen(bot, aiSpd * 1.12, vatCans);
            }

            case COVER -> {
                // ── P2: Tìm điểm nấp → A* đến đó ──
                CoverSystem.DiemNap cp = cover != null
                    ? cover.timDiemNap(bot, target, vatCans, infMap) : null;
                if (cp == null) { trangThai = TrangThaiBT.ENGAGE; break; }
                AStar.HuongDi dir = cauHinh.isDungAStar()
                    ? AStar.timDuong(bot.getX(), bot.getY(), cp.x(), cp.y(), walkGrid, gw, gh)
                    : null;
                bot.setHuongX(dir != null ? dir.dx() : (int)Math.signum(cp.x()-bot.getX()));
                bot.setHuongY(dir != null ? dir.dy() : (int)Math.signum(cp.y()-bot.getY()));
                diChuyen(bot, aiSpd, vatCans);
            }

            case CHASE -> {
                // ── P3: A* đuổi theo + SA né đạn trong khi chạy ──
                AStar.HuongDi dir = cauHinh.isDungAStar()
                    ? AStar.timDuong(bot.getX(), bot.getY(), target.getX(), target.getY(), walkGrid, gw, gh)
                    : null;
                AStar.HuongDi finalDir = dir;
                if (cauHinh.isDungSimulatedAnnealing() && sa != null && infMap != null) {
                    AStar.HuongDi saDir = sa.neDan(bot, infMap, vatCans);
                    if (saDir != null) finalDir = saDir;
                }
                bot.setHuongX(finalDir != null ? finalDir.dx() : (int)Math.signum(target.getX()-bot.getX()));
                bot.setHuongY(finalDir != null ? finalDir.dy() : (int)Math.signum(target.getY()-bot.getY()));
                diChuyen(bot, aiSpd, vatCans);
            }

            default -> {
                // ── P4 ENGAGE: Tấn công + cơ động chiến thuật ──
                engageMove(bot, target, cauHinh, aiSpd, vatCans, infMap, sa);
            }
        }
        return trangThai;
    }

    /** chuyenTrangThai — Priority Selector logic */
    private TrangThaiBT chuyenTrangThai(NhanVat bot, NhanVat target, CauHinhAI cauHinh,
                                          long now, List<VatCan> vatCans, List<ItemGame> items,
                                          InfluenceMap infMap, CoverSystem cover) {
        if (now - btTimer < MIN_STATE_MS) return trangThai;

        double hpR   = (double)bot.getHp() / bot.getMaxHp();
        boolean hasLOS = coTamNhin(bot, target, vatCans);
        double dist  = Math.hypot(bot.getX()-target.getX(), bot.getY()-target.getY());
        double danger = (cauHinh.isDungInfluenceMap() && infMap != null) ? infMap.layGiaTri(bot.getX(), bot.getY()) : 0;
        List<String> allowed = cauHinh.getBtStates();

        TrangThaiBT next = TrangThaiBT.ENGAGE;

        if (allowed.contains("HEAL") && hpR < 0.25 && timVatPhamGan(bot, items, "HP") != null)
            next = TrangThaiBT.HEAL;
        else if (allowed.contains("COVER") && cauHinh.isDungCoverSystem() &&
                 (bot.isDangNap() || (hpR < 0.38 && !hasLOS) || danger > 160))
            next = TrangThaiBT.COVER;
        else if (allowed.contains("CHASE") && !hasLOS && dist > 115)
            next = TrangThaiBT.CHASE;

        if (next != trangThai) { trangThai = next; btTimer = now; }
        return trangThai;
    }

    private void engageMove(NhanVat bot, NhanVat target, CauHinhAI cauHinh,
                              double aiSpd, List<VatCan> vatCans,
                              InfluenceMap infMap, SimulatedAnnealing sa) {
        boolean moved = false;
        // SA né đạn (ưu tiên 1)
        if (cauHinh.isDungSimulatedAnnealing() && sa != null && infMap != null) {
            AStar.HuongDi saDir = sa.neDan(bot, infMap, vatCans);
            if (saDir != null) { bot.setHuongX(saDir.dx()); bot.setHuongY(saDir.dy()); moved = true; }
        }
        // HC định vị (ưu tiên 2)
        if (!moved && cauHinh.isDungHillClimbing()) {
            AStar.HuongDi hcDir = HillClimbing.timHuongTot(bot, target, cauHinh, vatCans, infMap);
            if (hcDir.dx()!=0||hcDir.dy()!=0) { bot.setHuongX(hcDir.dx()); bot.setHuongY(hcDir.dy()); moved=true; }
        }
        // Di chuyển cơ bản (ưu tiên 3)
        if (!moved) {
            double d = Math.hypot(bot.getX()-target.getX(), bot.getY()-target.getY());
            if (d > 230) { bot.setHuongX((int)Math.signum(target.getX()-bot.getX())); bot.setHuongY((int)Math.signum(target.getY()-bot.getY())); }
            else if (d < 140) { bot.setHuongX(-(int)Math.signum(target.getX()-bot.getX())); bot.setHuongY(-(int)Math.signum(target.getY()-bot.getY())); }
        }
        diChuyen(bot, aiSpd, vatCans);
    }

    private void diChuyen(NhanVat bot, double spd, List<VatCan> vatCans) {
        int dx=bot.getHuongX(), dy=bot.getHuongY();
        if (dx==0&&dy==0) return;
        double mul=(dx!=0&&dy!=0)?0.7071:1.0;
        double nx=bot.getX()+dx*spd*mul;
        double ny=bot.getY()+dy*spd*mul;
        final int TS=40, W=1100, H=650;
        if (nx>=0&&nx<=W-TS&&!vatCanBlock(nx,bot.getY(),vatCans)) bot.setX(nx);
        if (ny>=0&&ny<=H-TS&&!vatCanBlock(bot.getX(),ny,vatCans)) bot.setY(ny);
    }

    private boolean vatCanBlock(double x, double y, List<VatCan> vatCans) {
        final int TS=40;
        for (VatCan o:vatCans)
            if (x<o.getX()+o.getRong()&&x+TS>o.getX()&&y<o.getY()+o.getCao()&&y+TS>o.getY()) return true;
        return false;
    }

    private boolean coTamNhin(NhanVat a, NhanVat b, List<VatCan> vatCans) {
        double ax=a.getX()+20,ay=a.getY()+20,bx=b.getX()+20,by=b.getY()+20;
        double dx=bx-ax,dy=by-ay,len=Math.hypot(dx,dy);
        if (len<1) return true;
        int steps=Math.min(14,(int)Math.ceil(len/35));
        for (int i=1;i<steps;i++) {
            double t=(double)i/steps,cx=ax+dx*t,cy=ay+dy*t;
            for (VatCan o:vatCans)
                if (cx>=o.getX()&&cx<=o.getX()+o.getRong()&&cy>=o.getY()&&cy<=o.getY()+o.getCao()) return false;
        }
        return true;
    }

    private ItemGame timVatPhamGan(NhanVat pos, List<ItemGame> items, String type) {
        ItemGame best=null; double bd=Double.MAX_VALUE;
        for (ItemGame it:items) {
            if (!it.getLoai().equals(type)) continue;
            double d=Math.hypot(pos.getX()-it.getX(), pos.getY()-it.getY());
            if (d<bd) { bd=d; best=it; }
        }
        return best;
    }

    public TrangThaiBT layTrangThai() { return trangThai; }
    public void datLai() { trangThai=TrangThaiBT.ENGAGE; btTimer=0; }
}
