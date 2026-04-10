package com.stickman.model;

import lombok.Data;
import java.util.List;

/**
 * TrangThaiGame — Snapshot trạng thái game tại 1 thời điểm
 * Được truyền giữa AI engine → controller → frontend
 */
@Data
public class TrangThaiGame {
    private List<NhanVat> nhanVats;
    private List<Dan>     vienDans;
    private List<VatCan>  vatCans;
    private CauHinhAI     cauHinhAI;
    private long          thoiGianHienTai;
    private boolean       dangChay;
}
