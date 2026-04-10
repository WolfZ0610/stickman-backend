package com.stickman.ai;

import com.stickman.model.NhanVat;
import com.stickman.model.VatCan;
import com.stickman.model.CauHinhAI;
import java.util.List;

/**
 * HillClimbing — Định vị chiến thuật tối ưu cục bộ
 * ═══════════════════════════════════════════════════════════════
 * Chức năng : Đánh giá 8 hướng di chuyển + đứng yên, chọn hướng
 *             cho điểm đánh giá (score) cao nhất.
 * Thuật toán: Greedy local search — không backtrack.
 *             Heuristic đa yếu tố:
 *               +110 : có tầm nhìn LOS đến mục tiêu
 *               +90  : khoảng cách gần lý tưởng (170-230px)
 *               -70  : gần tường biên bản đồ
 *               -190 : trong góc chết (2 tường gặp nhau)
 *               -IM  : vùng có nguy hiểm (nhân 0.72)
 *               +22  : gần obstacle (có cover tiềm năng)
 * Input     : bot, target, cauHinh (bao gồm cờ dungInfluenceMap),
 *             influenceMap (để tính danger score)
 * Output    : HuongDi {dx, dy} hướng tốt nhất
 * Khi gọi  : BehaviorTree.tick() state ENGAGE (ưu tiên sau SA)
 * ═══════════════════════════════════════════════════════════════
 */
public class HillClimbing {

    private static final int HC_STEP = 80;   // bước leo = 80px (2 grid cells)
    private static final int W = 1100, H = 650, TS = 40;

    private static final int[][] DIRS8 = {
        {1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,1},{1,-1},{-1,-1},{0,0}
    };

    /**
     * timHuongTot — Tìm hướng di chuyển tốt nhất
     * @param bot          bot cần điều hướng
     * @param target       mục tiêu (player người chơi)
     * @param cauHinh      cấu hình AI ải/mức
     * @param vatCans      danh sách vật cản
     * @param infMap       influence map hiện tại (null nếu không dùng IM)
     * @return {dx, dy} hướng tốt nhất; {0,0} nếu đứng yên là tốt nhất
     */
    public static AStar.HuongDi timHuongTot(NhanVat bot, NhanVat target,
                                             CauHinhAI cauHinh, List<VatCan> vatCans,
                                             InfluenceMap infMap) {
        double bestScore = tinhDiem(bot.getX(), bot.getY(), target, cauHinh, vatCans, infMap);
        int bestDx = 0, bestDy = 0;

        for (int[] d : DIRS8) {
            if (d[0] == 0 && d[1] == 0) continue;
            double tx = bot.getX() + d[0] * HC_STEP;
            double ty = bot.getY() + d[1] * HC_STEP;
            if (!coTheDung(tx, ty, vatCans)) continue;
            double s = tinhDiem(tx, ty, target, cauHinh, vatCans, infMap);
            if (s > bestScore) {
                bestScore = s;
                bestDx = d[0]; bestDy = d[1];
            }
        }
        return new AStar.HuongDi(bestDx, bestDy);
    }

    /**
     * tinhDiem — Tính điểm heuristic đa yếu tố tại vị trí (px, py)
     */
    public static double tinhDiem(double px, double py, NhanVat target,
                                   CauHinhAI cauHinh, List<VatCan> vatCans,
                                   InfluenceMap infMap) {
        double s = 0;
        double dist = Math.hypot(px - target.getX(), py - target.getY());

        // 1. Tầm nhìn LOS đến mục tiêu
        if (coTamNhin(px, py, target.getX(), target.getY(), vatCans)) s += 110;

        // 2. Duy trì khoảng cách lý tưởng
        double idealDist = 200;
        s += Math.max(0, 90 - Math.abs(dist - idealDist) * 0.55);

        // 3. Tránh tường biên
        if (px < 45 || px > W - TS - 45) s -= 70;
        if (py < 45 || py > H - TS - 45) s -= 70;

        // 4. Phạt góc chết (2 tường cùng lúc)
        boolean nearX = (px < 75 || px > W - TS - 75);
        boolean nearY = (py < 75 || py > H - TS - 75);
        if (nearX && nearY) s -= 190;

        // 5. Influence Map (chỉ khi được bật)
        if (cauHinh.isDungInfluenceMap() && infMap != null) {
            s -= infMap.layGiaTri(px, py) * 0.72;
        }

        // 6. Gần obstacle → có cover tiềm năng
        for (VatCan o : vatCans) {
            double dx = px - (o.getX() + o.getRong()/2);
            double dy = py - (o.getY() + o.getCao()/2);
            if (Math.hypot(dx, dy) < 82) { s += 22; break; }
        }
        return s;
    }

    /** coTamNhin — Kiểm tra LOS giữa 2 điểm */
    private static boolean coTamNhin(double ax, double ay, double bx, double by,
                                      List<VatCan> vatCans) {
        double dx = bx-ax, dy = by-ay, len = Math.hypot(dx, dy);
        if (len < 1) return true;
        int steps = Math.min(14, (int)Math.ceil(len / 35));
        for (int i = 1; i < steps; i++) {
            double t = (double)i/steps;
            double cx = ax+dx*t, cy = ay+dy*t;
            for (VatCan o : vatCans) {
                if (cx>=o.getX() && cx<=o.getX()+o.getRong() &&
                    cy>=o.getY() && cy<=o.getY()+o.getCao()) return false;
            }
        }
        return true;
    }

    /** coTheDung — Kiểm tra vị trí có thể đứng được không */
    private static boolean coTheDung(double x, double y, List<VatCan> vatCans) {
        if (x<0||x>W-TS||y<0||y>H-TS) return false;
        for (VatCan o : vatCans) {
            if (x<o.getX()+o.getRong() && x+TS>o.getX() &&
                y<o.getY()+o.getCao()  && y+TS>o.getY()) return false;
        }
        return true;
    }
}
