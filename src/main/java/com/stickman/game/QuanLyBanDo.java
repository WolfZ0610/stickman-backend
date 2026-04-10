package com.stickman.game;

import com.stickman.model.*;
import org.springframework.stereotype.Component;
import java.util.*;

/** QuanLyBanDo — Xây dựng bản đồ, walk grid, spawn items */
@Component
public class QuanLyBanDo {

    public List<VatCan> xayDungBanDo(CauHinhAI cfg) {
        return switch (cfg.getIdAi()) {
            case 1 -> mapAi1();
            case 2 -> mapAi2();
            case 3 -> mapAi3();
            case 4 -> mapAi4();
            case 5 -> mapAi5();
            default -> mapNgauNhien();
        };
    }

    private List<VatCan> mapAi1() {
        return List.of(
            vc(500,270,100,110), vc(200,160,80,70), vc(820,160,80,70), vc(200,420,80,70), vc(820,420,80,70));
    }
    private List<VatCan> mapAi2() {
        return List.of(
            vc(300,80,40,180), vc(300,390,40,180), vc(760,80,40,180), vc(760,390,40,180),
            vc(500,200,100,40), vc(500,410,100,40), vc(180,260,60,60), vc(860,260,60,60));
    }
    private List<VatCan> mapAi3() {
        return List.of(
            vc(420,230,260,180), vc(120,100,80,80), vc(900,100,80,80), vc(120,470,80,80), vc(900,470,80,80),
            vc(270,295,120,40), vc(710,295,120,40));
    }
    private List<VatCan> mapAi4() {
        return List.of(
            vc(220,100,40,160), vc(220,390,40,160), vc(260,280,100,40),
            vc(840,100,40,160), vc(840,390,40,160), vc(740,280,100,40),
            vc(490,160,120,40), vc(490,450,120,40), vc(530,280,40,90),
            vc(370,180,50,50), vc(680,400,50,50));
    }
    private List<VatCan> mapAi5() {
        return List.of(
            vc(525,280,50,90), vc(200,180,70,70), vc(160,380,55,55), vc(320,290,45,80),
            vc(830,180,70,70), vc(880,400,55,55), vc(730,290,45,80),
            vc(430,100,80,45), vc(590,100,80,45), vc(430,510,80,45), vc(590,510,80,45),
            vc(310,130,45,45), vc(745,480,45,45));
    }
    private List<VatCan> mapNgauNhien() {
        List<VatCan> r = new ArrayList<>();
        Random rng = new Random();
        r.add(vc(rng.nextInt(100)+490, rng.nextInt(60)+270, rng.nextInt(60)+60, rng.nextInt(40)+40));
        for (int i=0;i<8;i++) {
            int x=rng.nextInt(760)+120, y=rng.nextInt(490)+80;
            if (x<160&&y<160) continue; if (x>900&&y>450) continue;
            r.add(vc(x, y, rng.nextInt(80)+45, rng.nextInt(80)+45));
        }
        return r;
    }

    public boolean[][] xayDungWalkGrid(List<VatCan> vatCans, int W, int H, int cell) {
        int gw=(int)Math.ceil((double)W/cell), gh=(int)Math.ceil((double)H/cell);
        boolean[][] grid=new boolean[gh][gw];
        for (int gy=0;gy<gh;gy++) for (int gx=0;gx<gw;gx++) {
            double x=gx*cell, y=gy*cell;
            grid[gy][gx]=vatCans.stream().noneMatch(o ->
                x<o.getX()+o.getRong()&&x+cell>o.getX()&&y<o.getY()+o.getCao()&&y+cell>o.getY());
        }
        return grid;
    }

    public List<ItemGame> xuatHienItems(List<VatCan> vatCans, int soHP, int soAmmo) {
        List<ItemGame> items = new ArrayList<>();
        Random rng = new Random();
        for (int i=0;i<soHP;i++)   items.add(taoItem("HP",   vatCans, items, rng));
        for (int i=0;i<soAmmo;i++) items.add(taoItem("AMMO", vatCans, items, rng));
        return items;
    }

    private ItemGame taoItem(String loai, List<VatCan> vatCans, List<ItemGame> existing, Random rng) {
        double x,y; int tries=0;
        do { x=55+rng.nextDouble()*990; y=55+rng.nextDouble()*540; tries++; }
        while (tries<40 && (
            vatCans.stream().anyMatch(o->x<o.getX()+o.getRong()+10&&x+26>o.getX()-10&&y<o.getY()+o.getCao()+10&&y+26>o.getY()-10) ||
            existing.stream().anyMatch(e->Math.hypot(e.getX()-x,e.getY()-y)<36)));
        return ItemGame.builder().loai(loai).x(x).y(y).kichThuoc(26).id(System.nanoTime()).build();
    }

    private VatCan vc(double x,double y,double w,double h){return VatCan.builder().x(x).y(y).rong(w).cao(h).build();}
}
