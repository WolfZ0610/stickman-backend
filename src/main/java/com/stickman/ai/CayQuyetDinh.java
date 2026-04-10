package com.stickman.ai;

import com.stickman.model.*;
import java.util.*;

/**
 * CayQuyetDinh — Cây Quyết Định (THUẬT TOÁN CHÍNH)
 * ═══════════════════════════════════════════════════════════════
 * Chức năng : Bộ điều phối trung tâm cho AI bot.
 *             Thu thập trạng thái → kiểm tra điều kiện cây → chọn
 *             hành động → gọi thuật toán bổ trợ phù hợp.
 * Thuật toán: Decision Tree với Priority Selector.
 *             Là THUẬT TOÁN CHÍNH — điều phối tất cả thuật toán khác.
 *
 * Bước 1: Thu thập dữ liệu đầu vào (vị trí, HP, LOS, danger...)
 * Bước 2: Kiểm tra điều kiện theo thứ tự ưu tiên
 * Bước 3: Chọn nhánh hành động
 * Bước 4: Gọi thuật toán bổ trợ (A*, HC, SA, IM, Cover, FSM)
 * Bước 5: Trả ra hành động cuối cùng cho bot
 *
 * Quan hệ với BehaviorTree:
 *   Campaign: CayQuyetDinh là chính, BT không dùng
 *   Practice mức DỄ/TRUNG: BehaviorTree là chính, CayQuyetDinh phụ
 *   Practice SIÊU KHÓ: cả 2 (BT chính, CayQuyetDinhPhu điều chỉnh tham số)
 *
 * Input     : TrangThaiGame (toàn bộ trạng thái game)
 * Output    : HanhDongAI (enum hành động)
 * Khi gọi  : QuanLyAI.capNhat() mỗi frame
 * Ảnh hưởng: Gọi và phối hợp tất cả 7 thuật toán còn lại
 * ═══════════════════════════════════════════════════════════════
 */
public class CayQuyetDinh {

    public enum HanhDongAI {
        TAN_CONG,   // có LOS, trong tầm → bắn + cơ động
        TRUY_DUOI,  // mất LOS → A* đến mục tiêu
        AN_NAP,     // HP thấp / reload → Cover System
        NE_DAN,     // danger cao → SA né
        RUT_LUI,    // HP cực thấp → sprint ra xa
        TUAN_TRA,   // mục tiêu quá xa → di chuyển tuần tra
        LAY_HP,     // HP thấp + có item → sprint lấy HP
    }

    // Ngưỡng điều kiện
    private static final double HP_RUT_LUI     = 0.15;
    private static final double HP_AN_NAP      = 0.35;
    private static final double HP_LAY_HP      = 0.28;
    private static final double DIST_TAN_CONG  = 380;
    private static final double DIST_TUAN_TRA  = 500;
    private static final double DANGER_NE      = 80;

    // Các instance thuật toán bổ trợ
    private final InfluenceMap       influenceMap;
    private final CoverSystem        coverSystem;
    private final SimulatedAnnealing simulatedAnnealing;
    private final MaTranChuyenTrangThai matranFSM;
    private final BehaviorTree       behaviorTree;

    // Walk grid cho A*
    private boolean[][] walkGrid;
    private int gw, gh;

    public CayQuyetDinh(int canvasW, int canvasH) {
        this.influenceMap    = new InfluenceMap(canvasW, canvasH);
        this.coverSystem     = new CoverSystem();
        this.simulatedAnnealing = new SimulatedAnnealing();
        this.matranFSM       = new MaTranChuyenTrangThai();
        this.behaviorTree    = new BehaviorTree();
        this.gw = (int)Math.ceil((double)canvasW / AStar.GRID_CELL);
        this.gh = (int)Math.ceil((double)canvasH / AStar.GRID_CELL);
    }

    /**
     * xuLy — Điểm vào chính mỗi frame AI
     * @return HanhDongAI đã thực hiện
     */
    public HanhDongAI xuLy(NhanVat bot, NhanVat target, CauHinhAI cfg,
                            List<VatCan> vatCans, List<Dan> vienDans,
                            List<ItemGame> items, long now) {

        double aiSpd = bot.getTocDo() * cfg.getHeSoTocDoAI();

        // ─── BƯỚC 1: Thu thập dữ liệu ───
        double hpRatio  = (double)bot.getHp() / bot.getMaxHp();
        double dist     = Math.hypot(bot.getX()-target.getX(), bot.getY()-target.getY());
        boolean hasLOS  = kiemTraTamNhin(bot, target, vatCans);

        // Cập nhật Influence Map
        if (cfg.isDungInfluenceMap()) {
            influenceMap.capNhat(vienDans, vatCans, target.getId(), now);
        }
        double danger = cfg.isDungInfluenceMap() ? influenceMap.layGiaTri(bot.getX(), bot.getY()) : 0;

        // Cập nhật FSM
        if (cfg.isDungMaTranChuyenTT()) {
            matranFSM.capNhat(bot, target, vatCans, now);
            aiSpd *= matranFSM.heSoTocDo();
        }

        // ─── BƯỚC 2 & 3: Kiểm tra điều kiện cây quyết định ───

        // Nếu dùng BehaviorTree làm chính (practice mode)
        if (cfg.isDungBehaviorTree()) {
            behaviorTree.tick(bot, target, cfg, now, aiSpd, vatCans, items,
                    cfg.isDungInfluenceMap() ? influenceMap : null,
                    cfg.isDungCoverSystem()  ? coverSystem  : null,
                    walkGrid, gw, gh,
                    cfg.isDungSimulatedAnnealing() ? simulatedAnnealing : null);
            return HanhDongAI.TAN_CONG; // BT đã xử lý
        }

        // ─── Campaign: Cây Quyết Định là chính ───

        // P0: Rút lui khẩn cấp
        if (hpRatio < HP_RUT_LUI) {
            thucHienRutLui(bot, target, aiSpd, vatCans);
            return HanhDongAI.RUT_LUI;
        }

        // P1: Lấy HP nếu HP thấp + có item HP
        ItemGame hpItem = timVatPhamGan(bot, items, "HP");
        if (hpRatio < HP_LAY_HP && hpItem != null) {
            thucHienLayHP(bot, hpItem, aiSpd, vatCans, cfg);
            return HanhDongAI.LAY_HP;
        }

        // P2: Né đạn (SA) nếu danger cao
        if (cfg.isDungSimulatedAnnealing() && danger > DANGER_NE) {
            AStar.HuongDi saDir = simulatedAnnealing.neDan(bot, influenceMap, vatCans);
            if (saDir != null) {
                bot.setHuongX(saDir.dx()); bot.setHuongY(saDir.dy());
                diChuyen(bot, aiSpd, vatCans);
                return HanhDongAI.NE_DAN;
            }
        }

        // P3: Ẩn nấp nếu HP thấp hoặc reload
        if (cfg.isDungCoverSystem() && (bot.isDangNap() || hpRatio < HP_AN_NAP)) {
            CoverSystem.DiemNap cp = coverSystem.timDiemNap(bot, target, vatCans,
                    cfg.isDungInfluenceMap() ? influenceMap : null);
            if (cp != null) {
                AStar.HuongDi dir = cfg.isDungAStar()
                    ? AStar.timDuong(bot.getX(), bot.getY(), cp.x(), cp.y(), walkGrid, gw, gh) : null;
                bot.setHuongX(dir != null ? dir.dx() : (int)Math.signum(cp.x()-bot.getX()));
                bot.setHuongY(dir != null ? dir.dy() : (int)Math.signum(cp.y()-bot.getY()));
                diChuyen(bot, aiSpd, vatCans);
                return HanhDongAI.AN_NAP;
            }
        }

        // P4: Truy đuổi nếu mất LOS
        if (!hasLOS && dist > 115) {
            if (cfg.isDungAStar()) {
                AStar.HuongDi dir = AStar.timDuong(bot.getX(), bot.getY(),
                        target.getX(), target.getY(), walkGrid, gw, gh);
                if (dir != null) { bot.setHuongX(dir.dx()); bot.setHuongY(dir.dy()); }
                else { bot.setHuongX((int)Math.signum(target.getX()-bot.getX())); bot.setHuongY((int)Math.signum(target.getY()-bot.getY())); }
            }
            diChuyen(bot, aiSpd, vatCans);
            return HanhDongAI.TRUY_DUOI;
        }

        // P5: Tuần tra nếu quá xa
        if (dist > DIST_TUAN_TRA) {
            bot.setHuongX((int)Math.signum(target.getX()-bot.getX()));
            bot.setHuongY((int)Math.signum(target.getY()-bot.getY()));
            diChuyen(bot, aiSpd * 0.7, vatCans);
            return HanhDongAI.TUAN_TRA;
        }

        // P6: Tấn công (mặc định)
        thucHienTanCong(bot, target, cfg, aiSpd, vatCans);
        return HanhDongAI.TAN_CONG;
    }

    private void thucHienRutLui(NhanVat bot, NhanVat target, double aiSpd, List<VatCan> vatCans) {
        bot.setHuongX(-(int)Math.signum(target.getX()-bot.getX()));
        bot.setHuongY(-(int)Math.signum(target.getY()-bot.getY()));
        diChuyen(bot, aiSpd * 1.2, vatCans);
    }

    private void thucHienLayHP(NhanVat bot, ItemGame hp, double aiSpd,
                                List<VatCan> vatCans, CauHinhAI cfg) {
        AStar.HuongDi dir = cfg.isDungAStar()
            ? AStar.timDuong(bot.getX(), bot.getY(), hp.getX(), hp.getY(), walkGrid, gw, gh) : null;
        bot.setHuongX(dir!=null ? dir.dx() : (int)Math.signum(hp.getX()-bot.getX()));
        bot.setHuongY(dir!=null ? dir.dy() : (int)Math.signum(hp.getY()-bot.getY()));
        diChuyen(bot, aiSpd * 1.1, vatCans);
    }

    private void thucHienTanCong(NhanVat bot, NhanVat target, CauHinhAI cfg,
                                   double aiSpd, List<VatCan> vatCans) {
        // SA né đạn trong khi tấn công
        if (cfg.isDungSimulatedAnnealing()) {
            AStar.HuongDi saDir = simulatedAnnealing.neDan(bot, influenceMap, vatCans);
            if (saDir != null) { bot.setHuongX(saDir.dx()); bot.setHuongY(saDir.dy()); diChuyen(bot,aiSpd,vatCans); return; }
        }
        // HC định vị chiến thuật
        if (cfg.isDungHillClimbing()) {
            AStar.HuongDi hcDir = HillClimbing.timHuongTot(bot, target, cfg, vatCans,
                    cfg.isDungInfluenceMap() ? influenceMap : null);
            if (hcDir.dx()!=0||hcDir.dy()!=0) { bot.setHuongX(hcDir.dx()); bot.setHuongY(hcDir.dy()); diChuyen(bot,aiSpd,vatCans); return; }
        }
        // Di chuyển cơ bản
        double d=Math.hypot(bot.getX()-target.getX(),bot.getY()-target.getY());
        if (d>230) { bot.setHuongX((int)Math.signum(target.getX()-bot.getX())); bot.setHuongY((int)Math.signum(target.getY()-bot.getY())); }
        else if (d<150) { bot.setHuongX(-(int)Math.signum(target.getX()-bot.getX())); bot.setHuongY(-(int)Math.signum(target.getY()-bot.getY())); }
        diChuyen(bot, aiSpd, vatCans);
    }

    private void diChuyen(NhanVat bot, double spd, List<VatCan> vatCans) {
        int dx=bot.getHuongX(), dy=bot.getHuongY();
        if (dx==0&&dy==0) return;
        double mul=(dx!=0&&dy!=0)?0.7071:1.0;
        double nx=bot.getX()+dx*spd*mul, ny=bot.getY()+dy*spd*mul;
        final int TS=40,W=1100,H=650;
        if (nx>=0&&nx<=W-TS&&!block(nx,bot.getY(),vatCans,TS)) bot.setX(nx);
        if (ny>=0&&ny<=H-TS&&!block(bot.getX(),ny,vatCans,TS)) bot.setY(ny);
    }

    private boolean block(double x, double y, List<VatCan> v, int TS) {
        for (VatCan o:v) if (x<o.getX()+o.getRong()&&x+TS>o.getX()&&y<o.getY()+o.getCao()&&y+TS>o.getY()) return true;
        return false;
    }

    private boolean kiemTraTamNhin(NhanVat a, NhanVat b, List<VatCan> vatCans) {
        double ax=a.getX()+20,ay=a.getY()+20,bx=b.getX()+20,by=b.getY()+20;
        double dx=bx-ax,dy=by-ay,len=Math.hypot(dx,dy);
        if (len<1) return true;
        int steps=Math.min(14,(int)Math.ceil(len/35));
        for (int i=1;i<steps;i++) {
            double t=(double)i/steps,cx=ax+dx*t,cy=ay+dy*t;
            for (VatCan o:vatCans) if (cx>=o.getX()&&cx<=o.getX()+o.getRong()&&cy>=o.getY()&&cy<=o.getY()+o.getCao()) return false;
        }
        return true;
    }

    private ItemGame timVatPhamGan(NhanVat pos, List<ItemGame> items, String type) {
        ItemGame best=null; double bd=Double.MAX_VALUE;
        for (ItemGame it:items) {
            if (!it.getLoai().equals(type)) continue;
            double d=Math.hypot(pos.getX()-it.getX(),pos.getY()-it.getY());
            if (d<bd) { bd=d; best=it; }
        }
        return best;
    }

    // ── Setters / Getters ──
    public void datWalkGrid(boolean[][] grid, int gw, int gh) { this.walkGrid=grid; this.gw=gw; this.gh=gh; }
    public InfluenceMap layInfluenceMap() { return influenceMap; }
    public MaTranChuyenTrangThai layFSM() { return matranFSM; }
    public BehaviorTree layBT()           { return behaviorTree; }
    public double laySANhietDo()          { return simulatedAnnealing.layNhietDo(); }

    public void datLai() {
        influenceMap.datLai(); coverSystem.datLai();
        simulatedAnnealing.datLai(); matranFSM.datLai(); behaviorTree.datLai();
    }
}
