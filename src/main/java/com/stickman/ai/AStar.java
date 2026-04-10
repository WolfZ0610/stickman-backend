package com.stickman.ai;

import com.stickman.model.VatCan;
import java.util.*;

/**
 * AStar — Thuật toán A* tìm đường tránh vật cản
 * ═══════════════════════════════════════════════════════════════
 * Chức năng : Tìm đường ngắn nhất từ điểm start đến goal,
 *             tránh tất cả VatCan trên bản đồ.
 * Thuật toán: A* với Binary Min-Heap O(log n),
 *             heuristic Octile Distance (admissible cho 8 hướng),
 *             corner-cut guard để không đi xuyên góc tường.
 * Input     : (sx,sy) điểm xuất phát, (tx,ty) điểm đích,
 *             vatCans danh sách vật cản, walkGrid grid đi được
 * Output    : HuongDi {dx, dy} bước đầu tiên cần đi; null nếu không tìm được
 * Giới hạn  : 350 iterations để không lag frame
 * Khi gọi  : BehaviorTree.tick() khi state CHASE hoặc COVER
 * Ảnh hưởng: tabConsole.logAStar() để hiển thị từng bước
 * ═══════════════════════════════════════════════════════════════
 */
public class AStar {

    /** Kích thước ô grid (px) — đồng bộ với frontend */
    public static final int GRID_CELL = 40;
    /** Giới hạn số bước tìm kiếm */
    private static final int MAX_ITER = 350;

    /** Kết quả bước đi đầu tiên */
    public record HuongDi(int dx, int dy) {}

    /**
     * timDuong — Tìm đường từ (sx,sy) đến (tx,ty)
     * @param sx       x xuất phát (pixel)
     * @param sy       y xuất phát (pixel)
     * @param tx       x đích (pixel)
     * @param ty       y đích (pixel)
     * @param walkGrid grid boolean [gy][gx] = true nếu đi được
     * @param gw       số ô ngang
     * @param gh       số ô dọc
     * @return HuongDi bước đầu tiên, hoặc null nếu không tìm được
     */
    public static HuongDi timDuong(double sx, double sy,
                                    double tx, double ty,
                                    boolean[][] walkGrid, int gw, int gh) {
        int sgx = clamp((int)(sx / GRID_CELL), 0, gw-1);
        int sgy = clamp((int)(sy / GRID_CELL), 0, gh-1);
        int egx = clamp((int)(tx / GRID_CELL), 0, gw-1);
        int egy = clamp((int)(ty / GRID_CELL), 0, gh-1);
        if (sgx == egx && sgy == egy) return null;

        int N      = gw * gh;
        float[] gCost = new float[N];
        int[]   parent= new int[N];
        boolean[] closed = new boolean[N];
        Arrays.fill(gCost, Float.MAX_VALUE);
        Arrays.fill(parent, -1);

        int startI = sgy * gw + sgx;
        int goalI  = egy * gw + egx;
        gCost[startI] = 0;

        // Binary Min-Heap: [fCost, nodeIndex]
        PriorityQueue<int[]> heap = new PriorityQueue<>(
            Comparator.comparingDouble(a -> a[0])
        );
        heap.add(new int[]{0, startI});

        int[] DX4 = {1,-1,0,0};
        int[] DY4 = {0,0,1,-1};
        int[] DX8 = {1,-1,1,-1};
        int[] DY8 = {1,1,-1,-1};

        int iter = 0;
        while (!heap.isEmpty() && iter++ < MAX_ITER) {
            int[] top = heap.poll();
            int cur = top[1];
            if (cur == goalI) break;
            if (closed[cur]) continue;
            closed[cur] = true;
            int cx = cur % gw, cy = cur / gw;

            // 4 hướng thẳng (cost=1.0)
            for (int d = 0; d < 4; d++) {
                int nx = cx+DX4[d], ny = cy+DY4[d];
                if (nx<0||nx>=gw||ny<0||ny>=gh) continue;
                if (!walkGrid[ny][nx]) continue;
                int ni = ny*gw+nx;
                if (closed[ni]) continue;
                float ng = gCost[cur] + 1.0f;
                if (ng < gCost[ni]) {
                    gCost[ni] = ng;
                    parent[ni] = cur;
                    int h = octileHeur(nx, ny, egx, egy);
                    heap.add(new int[]{(int)(ng*10)+h, ni});
                }
            }
            // 4 hướng chéo (cost=√2), không cắt góc tường
            for (int d = 0; d < 4; d++) {
                int nx = cx+DX8[d], ny = cy+DY8[d];
                if (nx<0||nx>=gw||ny<0||ny>=gh) continue;
                if (!walkGrid[ny][nx]) continue;
                if (!walkGrid[cy][cx+DX8[d]] || !walkGrid[cy+DY8[d]][cx]) continue; // corner-cut guard
                int ni = ny*gw+nx;
                if (closed[ni]) continue;
                float ng = gCost[cur] + 1.414f;
                if (ng < gCost[ni]) {
                    gCost[ni] = ng;
                    parent[ni] = cur;
                    int h = octileHeur(nx, ny, egx, egy);
                    heap.add(new int[]{(int)(ng*10)+h, ni});
                }
            }
        }

        if (parent[goalI] == -1) return null;

        // Truy ngược path → lấy bước đầu tiên từ start
        int cur = goalI;
        while (parent[cur] != -1 && parent[cur] != startI) cur = parent[cur];
        int fx = (cur % gw) * GRID_CELL;
        int fy = (cur / gw) * GRID_CELL;
        return new HuongDi(
            (int)Math.signum(fx - sx),
            (int)Math.signum(fy - sy)
        );
    }

    /** Octile Distance heuristic (admissible cho 8 hướng) */
    private static int octileHeur(int ax, int ay, int bx, int by) {
        int dx = Math.abs(ax-bx), dy = Math.abs(ay-by);
        return (int)((Math.max(dx,dy) + (Math.sqrt(2)-1)*Math.min(dx,dy)) * 10);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
