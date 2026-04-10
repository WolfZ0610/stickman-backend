package com.stickman.controller;

import com.stickman.model.CauHinhAI;
import com.stickman.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * GameController — REST API game (leaderboard, result sync, AI config)
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class GameController {

    private final AuthService authService;
    public GameController(AuthService authService) { this.authService = authService; }

    @GetMapping("/leaderboard")
    public ResponseEntity<Map<String,Object>> leaderboard(@RequestParam(defaultValue="wins") String sort) {
        return ResponseEntity.ok(Map.of("players", authService.layLeaderboard(sort)));
    }

    @PostMapping("/result")
    public ResponseEntity<Map<String,Object>> dongBoKetQua(
            @RequestHeader(value="Authorization",required=false) String auth,
            @RequestBody Map<String,Object> body) {
        if (auth!=null&&auth.startsWith("Bearer ")) {
            String username = authService.xacThucToken(auth.substring(7));
            if (username!=null) {
                boolean won  = Boolean.TRUE.equals(body.get("won"));
                boolean draw = Boolean.TRUE.equals(body.get("draw"));
                int coins    = switch(body.get("mode") != null ? body.get("mode").toString() : "") {
                    case "campaign" -> won ? 40 : 10;
                    case "online"   -> won ? 50 : 15;
                    default         -> won ? 20 : 5;
                };
                authService.capNhatStats(username, won, draw, coins);
            }
        }
        return ResponseEntity.ok(Map.of("ok",true));
    }

    @GetMapping("/ai-config/{mode}/{id}")
    public ResponseEntity<Map<String,Object>> layCauHinhAI(
            @PathVariable String mode, @PathVariable String id) {
        CauHinhAI cfg = "campaign".equals(mode)
            ? switch(id) {
                case "1" -> CauHinhAI.taoAi1(); case "2" -> CauHinhAI.taoAi2();
                case "3" -> CauHinhAI.taoAi3(); case "4" -> CauHinhAI.taoAi4();
                default  -> CauHinhAI.taoAi5();
              }
            : CauHinhAI.taoPractice(id);
        return ResponseEntity.ok(Map.of("config", cfg));
    }
}
