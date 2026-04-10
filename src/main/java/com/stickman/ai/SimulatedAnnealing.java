package com.stickman.ai;

import com.stickman.model.NhanVat;
import com.stickman.model.VatCan;
import java.util.List;
import java.util.Random;

/**
 * SimulatedAnnealing — Né đạn thích nghi với nhiệt độ động
 * ═══════════════════════════════════════════════════════════════
 * Chức năng : Tìm hướng né tránh vùng nguy hiểm (đạn đang bay)
 *             bằng SA với nhiệt độ tăng khi nguy hiểm cao.
 * Thuật toán: Biến thể Hill Climbing có nhiệt độ:
 *   - Luôn chấp nhận bước cải thiện (delta < 0)
 *   - Chấp nhận bước xấu hơn với xác suất e^(-Δ/T)
 *   - T tăng khi danger tăng → tìm kiếm rộng hơn khi nguy hiểm
 *   - T làm nguội × 0.87 sau mỗi lần gọi
 *   - Quán tính: giữ hướng né trước nếu không tìm được mới
 * Input     : bot, influenceMap, vatCans
 * Output    : HuongDi {dx, dy} hướng né tốt nhất; null nếu an toàn
 * Khi gọi  : BehaviorTree.tick() state ENGAGE/CHASE (ưu tiên cao nhất)
 *             Chỉ kích hoạt khi danger >= 22
 * ═══════════════════════════════════════════════════════════════
 */
public class SimulatedAnnealing {

    private static final double NGUONG_NGUY_HIEM = 22.0;
    private static final double TEMP_MAX         = 130.0;
    private static final double LAM_NGUOI        = 0.87;
    private static final int    W = 1100, H = 650, TS = 40;

    private static final int[][] DIRS8 = {
        {1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,1},{1,-1},{-1,-1}
    };

    private double       nhietDo  = 10.0;
    private AStar.HuongDi huongCu = null;
    private final Random rng      = new Random();

    /**
     * neDan — Tính hướng né đạn tối ưu
     * @param bot      bot cần né
     * @param infMap   influence map hiện tại
     * @param vatCans  danh sách vật cản
     * @return hướng né tốt nhất, hoặc null nếu vùng an toàn
     */
    public AStar.HuongDi neDan(NhanVat bot, InfluenceMap infMap, List<VatCan> vatCans) {
        double curDanger = infMap.layGiaTri(bot.getX(), bot.getY());

        // Không kích hoạt khi nguy hiểm thấp
        if (curDanger < NGUONG_NGUY_HIEM) {
            nhietDo  = Math.max(5, nhietDo * 0.93);
            huongCu  = null;
            return null;
        }

        // Tăng nhiệt độ tỉ lệ với nguy hiểm
        nhietDo = Math.min(TEMP_MAX, nhietDo + curDanger * 0.22);

        AStar.HuongDi bestDir = null;
        double bestDelta = 0;

        for (int[] d : DIRS8) {
            double tx = bot.getX() + d[0] * bot.getTocDo() * 3.5;
            double ty = bot.getY() + d[1] * bot.getTocDo() * 3.5;
            if (!coTheDung(tx, ty, vatCans)) continue;

            double nd    = infMap.layGiaTri(tx, ty);
            double delta = nd - curDanger; // âm = hướng an toàn hơn

            if (delta < bestDelta) {
                bestDelta = delta;
                bestDir   = new AStar.HuongDi(d[0], d[1]);
            } else if (delta > 0 && delta < curDanger * 0.6) {
                // SA: chấp nhận bước xấu với xác suất theo nhiệt độ
                double prob = Math.exp(-delta / Math.max(1, nhietDo));
                if (rng.nextDouble() < prob) {
                    bestDir = new AStar.HuongDi(d[0], d[1]);
                }
            }
        }

        // Làm nguội SA
        nhietDo *= LAM_NGUOI;

        // Quán tính
        if (bestDir == null && huongCu != null) {
            double tx = bot.getX() + huongCu.dx() * bot.getTocDo() * 3.5;
            double ty = bot.getY() + huongCu.dy() * bot.getTocDo() * 3.5;
            if (coTheDung(tx, ty, vatCans)) bestDir = huongCu;
        }

        huongCu = bestDir;
        return bestDir;
    }

    public double layNhietDo() { return nhietDo; }

    public void datLai() {
        nhietDo = 10.0;
        huongCu = null;
    }

    private boolean coTheDung(double x, double y, List<VatCan> vatCans) {
        if (x<0||x>W-TS||y<0||y>H-TS) return false;
        for (VatCan o : vatCans) {
            if (x<o.getX()+o.getRong() && x+TS>o.getX() &&
                y<o.getY()+o.getCao()  && y+TS>o.getY()) return false;
        }
        return true;
    }
}
