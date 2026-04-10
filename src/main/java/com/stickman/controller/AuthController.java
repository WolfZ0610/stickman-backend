package com.stickman.controller;

import com.stickman.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * AuthController — REST API xác thực
 * POST /api/login, /api/register, /api/guest
 * GET  /api/me
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/login")
    public ResponseEntity<Map<String,Object>> dangNhap(@RequestBody Map<String,String> body) {
        String u=body.get("username"), p=body.get("password");
        if (u==null||p==null) return ResponseEntity.badRequest().body(Map.of("ok",false,"error","Thiếu thông tin"));
        Map<String,Object> r = authService.dangNhap(u, p);
        return (Boolean)r.get("ok") ? ResponseEntity.ok(r) : ResponseEntity.status(401).body(r);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String,Object>> dangKy(@RequestBody Map<String,String> body) {
        String u=body.get("username"), p=body.get("password");
        if (u==null||p==null) return ResponseEntity.badRequest().body(Map.of("ok",false,"error","Thiếu thông tin"));
        Map<String,Object> r = authService.dangKy(u, p);
        return (Boolean)r.get("ok") ? ResponseEntity.ok(r) : ResponseEntity.badRequest().body(r);
    }

    @PostMapping("/guest")
    public ResponseEntity<Map<String,Object>> guest() {
        return ResponseEntity.ok(Map.of("ok",true,"user",Map.of("username","Khách","stats",Map.of("wins",0,"losses",0))));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String,Object>> layThongTin(@RequestHeader(value="Authorization",required=false) String auth) {
        if (auth==null||!auth.startsWith("Bearer ")) return ResponseEntity.status(401).body(Map.of("error","Chưa đăng nhập"));
        String token=auth.substring(7);
        String username=authService.xacThucToken(token);
        if (username==null) return ResponseEntity.status(401).body(Map.of("error","Token không hợp lệ"));
        return ResponseEntity.ok(authService.layThongTin(username));
    }
}
