package com.stickman.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * CauHinhAI — Cấu hình thuật toán cho từng ải / mức luyện tập
 * Chỉ bật đúng thuật toán được chỉ định theo yêu cầu.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CauHinhAI {
    private int    idAi;              // 1-5 (campaign) hoặc 0 (practice)
    private String tenAi;
    private String cheDoChoi;         // "campaign" | "practice"

    // ── Bật/tắt từng thuật toán ──
    private boolean dungAStar;
    private boolean dungHillClimbing;
    private boolean dungSimulatedAnnealing;
    private boolean dungInfluenceMap;
    private boolean dungCoverSystem;
    private boolean dungBehaviorTree;
    private boolean dungCayQuyetDinh;
    private boolean dungMaTranChuyenTT;
    private boolean dungCayQuyetDinhPhu;  // Siêu khó

    // ── Danh sách trạng thái BT được phép ──
    private List<String> btStates;

    // ── Hệ số cân bằng ──
    private double heSoTocDoAI;
    private int    bonusHP;
    private int    boostTocBan;

    // ── Factory methods cho từng ải ──
    public static CauHinhAI taoAi1() {
        return CauHinhAI.builder()
            .idAi(1).tenAi("TIỀN TUYẾN").cheDoChoi("campaign")
            .dungAStar(true)
            .dungHillClimbing(false).dungSimulatedAnnealing(false)
            .dungInfluenceMap(false).dungCoverSystem(false)
            .dungBehaviorTree(false).dungCayQuyetDinh(true)
            .dungMaTranChuyenTT(false)
            .btStates(List.of("ENGAGE","CHASE"))
            .heSoTocDoAI(0.85).bonusHP(0).boostTocBan(0)
            .build();
    }
    public static CauHinhAI taoAi2() {
        return CauHinhAI.builder()
            .idAi(2).tenAi("CHIẾN TUYẾN").cheDoChoi("campaign")
            .dungAStar(true).dungMaTranChuyenTT(true)
            .dungHillClimbing(false).dungSimulatedAnnealing(false)
            .dungInfluenceMap(false).dungCoverSystem(false)
            .dungBehaviorTree(false).dungCayQuyetDinh(true)
            .btStates(List.of("ENGAGE","CHASE","HEAL"))
            .heSoTocDoAI(0.92).bonusHP(15).boostTocBan(30)
            .build();
    }
    public static CauHinhAI taoAi3() {
        return CauHinhAI.builder()
            .idAi(3).tenAi("VÙNG XUNG ĐỘT").cheDoChoi("campaign")
            .dungAStar(true).dungInfluenceMap(true)
            .dungHillClimbing(false).dungSimulatedAnnealing(false)
            .dungCoverSystem(false).dungMaTranChuyenTT(false)
            .dungBehaviorTree(false).dungCayQuyetDinh(true)
            .btStates(List.of("ENGAGE","CHASE","HEAL"))
            .heSoTocDoAI(1.0).bonusHP(30).boostTocBan(50)
            .build();
    }
    public static CauHinhAI taoAi4() {
        return CauHinhAI.builder()
            .idAi(4).tenAi("VÙNG NGUY HIỂM").cheDoChoi("campaign")
            .dungAStar(true).dungInfluenceMap(true)
            .dungHillClimbing(true).dungCoverSystem(true)
            .dungSimulatedAnnealing(false).dungMaTranChuyenTT(true)
            .dungBehaviorTree(false).dungCayQuyetDinh(true)
            .btStates(List.of("ENGAGE","CHASE","HEAL","COVER"))
            .heSoTocDoAI(1.08).bonusHP(50).boostTocBan(70)
            .build();
    }
    public static CauHinhAI taoAi5() {
        return CauHinhAI.builder()
            .idAi(5).tenAi("ĐỊA NGỤC").cheDoChoi("campaign")
            .dungAStar(true).dungInfluenceMap(true)
            .dungCoverSystem(true).dungSimulatedAnnealing(true)
            .dungHillClimbing(false).dungMaTranChuyenTT(true)
            .dungBehaviorTree(false).dungCayQuyetDinh(true)
            .btStates(List.of("ENGAGE","CHASE","HEAL","COVER"))
            .heSoTocDoAI(1.18).bonusHP(75).boostTocBan(100)
            .build();
    }
    public static CauHinhAI taoPractice(String mucDo) {
        return switch (mucDo) {
            case "easy" -> CauHinhAI.builder()
                .idAi(0).tenAi("DỄ").cheDoChoi("practice")
                .dungBehaviorTree(true).dungAStar(true).dungMaTranChuyenTT(true)
                .dungHillClimbing(false).dungInfluenceMap(false)
                .dungCoverSystem(false).dungSimulatedAnnealing(false)
                .dungCayQuyetDinh(false)
                .btStates(List.of("ENGAGE","CHASE"))
                .heSoTocDoAI(0.8).bonusHP(0).boostTocBan(0).build();
            case "medium" -> CauHinhAI.builder()
                .idAi(0).tenAi("TRUNG BÌNH").cheDoChoi("practice")
                .dungBehaviorTree(true).dungAStar(true).dungMaTranChuyenTT(true)
                .dungCayQuyetDinh(true)
                .dungHillClimbing(false).dungInfluenceMap(false)
                .dungCoverSystem(false).dungSimulatedAnnealing(false)
                .btStates(List.of("ENGAGE","CHASE","HEAL"))
                .heSoTocDoAI(0.92).bonusHP(15).boostTocBan(30).build();
            case "hard" -> CauHinhAI.builder()
                .idAi(0).tenAi("KHÓ").cheDoChoi("practice")
                .dungBehaviorTree(true).dungAStar(true).dungInfluenceMap(true)
                .dungCoverSystem(true).dungHillClimbing(true)
                .dungMaTranChuyenTT(true).dungCayQuyetDinh(false)
                .dungSimulatedAnnealing(false)
                .btStates(List.of("ENGAGE","CHASE","HEAL","COVER"))
                .heSoTocDoAI(1.0).bonusHP(30).boostTocBan(60).build();
            case "extreme" -> CauHinhAI.builder()
                .idAi(0).tenAi("SIÊU KHÓ").cheDoChoi("practice")
                .dungBehaviorTree(true).dungAStar(true).dungInfluenceMap(true)
                .dungCoverSystem(true).dungSimulatedAnnealing(true)
                .dungMaTranChuyenTT(true).dungCayQuyetDinh(true)
                .dungCayQuyetDinhPhu(true).dungHillClimbing(false)
                .btStates(List.of("ENGAGE","CHASE","HEAL","COVER"))
                .heSoTocDoAI(1.12).bonusHP(50).boostTocBan(90).build();
            default -> taoPractice("easy");
        };
    }
}
