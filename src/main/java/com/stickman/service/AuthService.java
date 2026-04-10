package com.stickman.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuthService — Xác thực người dùng (JWT in-memory, không cần DB)
 * Trong production nên thay bằng JPA + database thực sự.
 */
@Service
public class AuthService {

    @Value("${jwt.secret}")
    private String jwtSecret;
    @Value("${jwt.expiration:86400000}")
    private long   jwtExpiration;

    // In-memory user store (thay bằng DB trong production)
    private final Map<String, UserRecord> users = new ConcurrentHashMap<>();

    public record UserRecord(String username, String passwordHash, Map<String,Object> stats) {}

    /** dangKy — Đăng ký tài khoản mới */
    public Map<String, Object> dangKy(String username, String password) {
        if (username.length() < 3 || username.length() > 20)
            return err("Tên đăng nhập phải 3-20 ký tự");
        if (password.length() < 4)
            return err("Mật khẩu phải ít nhất 4 ký tự");
        if (users.containsKey(username.toLowerCase()))
            return err("Tên đăng nhập đã tồn tại");

        String hash = hashPass(password);
        users.put(username.toLowerCase(), new UserRecord(username, hash, new HashMap<>(Map.of("wins",0,"losses",0,"coins",0))));
        String token = taoToken(username);
        return ok(Map.of("token", token, "user", userInfo(username)));
    }

    /** dangNhap — Đăng nhập */
    public Map<String, Object> dangNhap(String username, String password) {
        UserRecord ur = users.get(username.toLowerCase());
        if (ur == null || !ur.passwordHash().equals(hashPass(password)))
            return err("Sai tên đăng nhập hoặc mật khẩu");
        return ok(Map.of("token", taoToken(username), "user", userInfo(username)));
    }

    /** xacThucToken — Xác thực JWT và trả về username */
    public String xacThucToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        } catch (Exception e) { return null; }
    }

    /** capNhatStats — Cập nhật wins/losses/coins */
    public void capNhatStats(String username, boolean won, boolean draw, int coins) {
        UserRecord ur = users.get(username.toLowerCase());
        if (ur == null) return;
        Map<String,Object> stats = ur.stats();
        if (!draw) {
            if (won) stats.merge("wins", 1, (a,b) -> (int)a+(int)b);
            else     stats.merge("losses", 1, (a,b) -> (int)a+(int)b);
        }
        stats.merge("coins", coins, (a,b) -> (int)a+(int)b);
    }

    /** layLeaderboard — Lấy top players */
    public List<Map<String,Object>> layLeaderboard(String sortBy) {
        return users.values().stream()
            .map(ur -> {
                Map<String,Object> m = new HashMap<>(ur.stats());
                m.put("username", ur.username());
                int wins=(int)m.getOrDefault("wins",0), losses=(int)m.getOrDefault("losses",0);
                int total=wins+losses;
                m.put("winRate", total>0 ? (double)wins/total : 0.0);
                return m;
            })
            .sorted((a,b) -> {
                if ("winrate".equals(sortBy)) return Double.compare((double)b.get("winRate"),(double)a.get("winRate"));
                return Integer.compare((int)b.get("wins"),(int)a.get("wins"));
            })
            .limit(50)
            .toList();
    }

    public Map<String, Object> layThongTin(String username) {
        if (username==null||!users.containsKey(username.toLowerCase())) return Map.of("error","Không tìm thấy");
        return Map.of("user", userInfo(username));
    }

    private String taoToken(String username) {
        return Jwts.builder()
            .subject(username)
            .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
            .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }

    private Map<String,Object> userInfo(String username) {
        UserRecord ur = users.get(username.toLowerCase());
        if (ur==null) return Map.of();
        return Map.of("username", ur.username(), "stats", ur.stats());
    }

    private String hashPass(String p) {
        // Simple hash — thay bằng BCrypt trong production
        return Integer.toHexString(Objects.hash(p, "stickman_salt_2025"));
    }

    private Map<String,Object> ok(Map<String,Object> data) {
        Map<String,Object> r=new HashMap<>(data); r.put("ok",true); return r;
    }
    private Map<String,Object> err(String msg) { return Map.of("ok",false,"error",msg); }
}
