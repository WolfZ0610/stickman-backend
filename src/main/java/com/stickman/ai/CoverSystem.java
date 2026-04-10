package com.stickman.ai;

import com.stickman.model.NhanVat;
import com.stickman.model.VatCan;
import java.util.List;

/**
 * CoverSystem — Tìm điểm nấp tối ưu sau vật cản
 * ═══════════════════════════════════════════════════════════════
 * Chức năng : Duyệt 4 điểm xung quanh mỗi obstacle, chọn điểm
 *             tốt nhất để bot nấp tránh đạn player.
 * Điều kiện : Điểm nấp PHẢI chặn LOS từ player đến bot.
 * Chấm điểm : Đa yếu tố:
 *   -dist_bot × 0.018    : gần bot → dễ đến
 *   +dist_player × 0.06  : xa player → an toàn
 *   -imDanger × 0.5      : tránh vùng đạn
 *   -55 nếu sát biên map : tránh bị kẹp góc
 * Cache     : 450ms COVER_TTL để không tính lại mỗi frame
 * Input     : bot, target, vatCans, influenceMap
 * Output    : DiemNap {x, y, score} hoặc null nếu không tìm được
 * Khi gọi  : BehaviorTree.tick() state COVER
 * ═══════════════════════════════════════════════════════════════
 */
public class CoverSystem {

    private static final int MARGIN   = 6;
    private static final int COVER_TTL= 450;    // ms
    private static final int W = 1100, H = 650, TS = 40;

    private DiemNap cachedCover = null;
    private long    cacheTick   = 0;

    /** DiemNap — kết quả tìm điểm nấp */
    public record DiemNap(double x, double y, double score) {}

    /**
     * timDiemNap — Tìm điểm nấp tối ưu
     * @param bot      bot cần nấp
     * @param target   mục tiêu (player) cần tránh đạn từ đó
     * @param vatCans  danh sách vật cản
     * @param infMap   influence map (null nếu không dùng)
     * @return DiemNap tốt nhất, hoặc null
     */
    public DiemNap timDiemNap(NhanVat bot, NhanVat target,
                               List<VatCan> vatCans, InfluenceMap infMap) {
        long now = System.currentTimeMillis();
        if (now - cacheTick < COVER_TTL && cachedCover != null) return cachedCover;
        cacheTick = now;

        double bestScore = Double.NEGATIVE_INFINITY;
        DiemNap bestPos = null;

        for (VatCan o : vatCans) {
            // 4 điểm xung quanh mỗi obstacle
            double[][] candidates = {
                {o.getX() - TS - MARGIN,            o.getY() + o.getCao()*0.5 - TS*0.5},
                {o.getX() + o.getRong() + MARGIN,    o.getY() + o.getCao()*0.5 - TS*0.5},
                {o.getX() + o.getRong()*0.5 - TS*0.5, o.getY() - TS - MARGIN},
                {o.getX() + o.getRong()*0.5 - TS*0.5, o.getY() + o.getCao() + MARGIN},
            };

            for (double[] c : candidates) {
                double cx = c[0], cy = c[1];
                if (!coTheDung(cx, cy, vatCans)) continue;

                // PHẢI chặn LOS từ player → vị trí nấp
                if (coTamNhin(cx, cy, target.getX(), target.getY(), vatCans)) continue;

                double score = 0;
                score -= Math.hypot(bot.getX()-cx, bot.getY()-cy) * 0.018;
                score += Math.min(185, Math.hypot(target.getX()-cx, target.getY()-cy)) * 0.06;
                if (infMap != null) score -= infMap.layGiaTri(cx, cy) * 0.5;
                if (cx<55||cx>W-TS-55||cy<55||cy>H-TS-55) score -= 55;

                if (score > bestScore) {
                    bestScore = score;
                    bestPos = new DiemNap(cx, cy, score);
                }
            }
        }

        cachedCover = bestPos;
        return bestPos;
    }

    public void datLai() { cachedCover = null; cacheTick = 0; }

    private boolean coTamNhin(double ax, double ay, double bx, double by,
                                List<VatCan> vatCans) {
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

    private boolean coTheDung(double x, double y, List<VatCan> vatCans) {
        if (x<0||x>W-TS||y<0||y>H-TS) return false;
        for (VatCan o:vatCans)
            if (x<o.getX()+o.getRong()&&x+TS>o.getX()&&
                y<o.getY()+o.getCao() &&y+TS>o.getY()) return false;
        return true;
    }
}
