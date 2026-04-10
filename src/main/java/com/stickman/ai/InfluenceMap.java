package com.stickman.ai;

import com.stickman.model.Dan;
import com.stickman.model.VatCan;
import java.util.List;

/**
 * InfluenceMap — Bản đồ nhiệt nguy hiểm theo quỹ đạo đạn
 * ═══════════════════════════════════════════════════════════════
 * Chức năng : Tính toán vùng nguy hiểm dựa trên vị trí và hướng
 *             của các viên đạn đang bay trên bản đồ.
 * Thuật toán: Mỗi đạn chiếu tia 12 bước theo hướng vận tốc.
 *             Các ô trên tia nhận giá trị nguy hiểm, lan sang
 *             4 ô kề (cross pattern). Time-decay: × 0.80 mỗi tick.
 * Input     : bullets (danh sách Dan), vatCans, W, H, idNguoiChoi
 * Output    : float[] influenceMap (IM_W × IM_H cells)
 *             imDangerAt(x,y) → giá trị nguy hiểm tại điểm (x,y)
 * Khi gọi  : Mỗi frame khi dungInfluenceMap = true
 * Ảnh hưởng: CayQuyetDinh, HillClimbing, SimulatedAnnealing đều đọc IM
 * ═══════════════════════════════════════════════════════════════
 */
public class InfluenceMap {

    public static final int IM_CELL = 40;
    private final int W, H, imW, imH;
    private final float[] map;
    private long lastUpdate = 0;

    public InfluenceMap(int canvasW, int canvasH) {
        this.W   = canvasW;
        this.H   = canvasH;
        this.imW = (int)Math.ceil((double)W / IM_CELL);
        this.imH = (int)Math.ceil((double)H / IM_CELL);
        this.map = new float[imW * imH];
    }

    /**
     * capNhat — Cập nhật influence map theo danh sách đạn
     * Throttle: 60ms để không tính toán quá nhiều
     * @param bullets     danh sách viên đạn hiện tại
     * @param vatCans     danh sách vật cản (đạn không xuyên tường)
     * @param idNguoiChoi id của player (bot chỉ né đạn của player)
     * @param now         thời gian hiện tại (ms)
     */
    public void capNhat(List<Dan> bullets, List<VatCan> vatCans, int idNguoiChoi, long now) {
        if (now - lastUpdate < 60) return;
        lastUpdate = now;

        // Time-decay: giảm 20% mỗi lần update
        for (int i = 0; i < map.length; i++) {
            map[i] *= 0.80f;
            if (map[i] < 0.5f) map[i] = 0;
        }

        for (Dan b : bullets) {
            if (b.getChuSoHuu() != idNguoiChoi) continue;
            double bspd = Math.hypot(b.getVx(), b.getVy());
            if (bspd < 0.1) continue;

            // Chiếu tia 12 bước theo hướng đạn
            for (int step = 0; step <= 12; step++) {
                double fx = b.getX() + b.getVx() * step * 1.8;
                double fy = b.getY() + b.getVy() * step * 1.8;
                if (fx < 0 || fx >= W || fy < 0 || fy >= H) break;

                // Dừng tại tường
                if (dungDenTuong(fx, fy, vatCans)) break;

                int gx = (int)(fx / IM_CELL);
                int gy = (int)(fy / IM_CELL);
                float w = Math.max(0, 1 - step * 0.08f);

                int ci = gy * imW + gx;
                if (ci >= 0 && ci < map.length)
                    map[ci] = Math.min(350, map[ci] + w * 160);

                // Lan sang 4 ô kề
                int[][] n4 = {{1,0},{-1,0},{0,1},{0,-1}};
                for (int[] d : n4) {
                    int nx = gx+d[0], ny = gy+d[1];
                    if (nx<0||nx>=imW||ny<0||ny>=imH) continue;
                    int ni = ny * imW + nx;
                    map[ni] = Math.min(200, map[ni] + w * 65);
                }
            }
        }
    }

    /**
     * layGiaTri — Lấy mức nguy hiểm tại điểm pixel (x,y)
     * @return 0.0 (an toàn) → 350.0 (rất nguy hiểm)
     */
    public float layGiaTri(double x, double y) {
        int gx = Math.max(0, Math.min(imW-1, (int)(x / IM_CELL)));
        int gy = Math.max(0, Math.min(imH-1, (int)(y / IM_CELL)));
        return map[gy * imW + gx];
    }

    /** layMang — Lấy toàn bộ mảng (cho frontend render heatmap) */
    public float[] layMang() { return map.clone(); }

    public int layImW() { return imW; }
    public int layImH() { return imH; }

    /** datLai — Reset map về 0 */
    public void datLai() {
        java.util.Arrays.fill(map, 0);
        lastUpdate = 0;
    }

    private boolean dungDenTuong(double fx, double fy, List<VatCan> vatCans) {
        for (VatCan o : vatCans) {
            if (fx >= o.getX()-4 && fx <= o.getX()+o.getRong()+4 &&
                fy >= o.getY()-4 && fy <= o.getY()+o.getCao()+4) return true;
        }
        return false;
    }
}
