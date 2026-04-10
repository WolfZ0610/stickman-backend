package com.stickman.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stickman.game.QuanLyGame;
import com.stickman.model.*;
import com.stickman.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * GameWebSocketHandler — WebSocket handler cho multiplayer
 * ════════════════════════════════════════════════════════════
 * Chức năng: Xử lý kết nối, matchmaking, sync game state.
 * Message types nhận:
 *   ping, auth, find_match, cancel, create_room, join_room,
 *   start_room, cancel_room, leave_room, player_update,
 *   bullet_spawn, hit_event
 * Message types gửi:
 *   pong, matched, game_start, player_update (relay),
 *   bullet_spawn (relay), hit_event (relay), game_over,
 *   room_created, room_joined, room_update, admin_alert
 * ════════════════════════════════════════════════════════════
 */
@Slf4j
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper   mapper    = new ObjectMapper();
    private final AuthService    authService;
    private final QuanLyGame     quanLyGame;

    // Session management
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String>  sessionUsername   = new ConcurrentHashMap<>();
    private final List<String>         hangChoiDoi       = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String>  phongChoiMap      = new ConcurrentHashMap<>(); // sessionId→roomId
    private final Map<String, List<String>> phongSessions = new ConcurrentHashMap<>(); // roomId→[sessionIds]
    private final Map<String, String>  phongChuPhong     = new ConcurrentHashMap<>(); // roomId→sessionId (chủ phòng)

    private static final int MAX_MATCH_WAIT_MS = 30000;

    public GameWebSocketHandler(AuthService authService, QuanLyGame quanLyGame) {
        this.authService = authService;
        this.quanLyGame  = quanLyGame;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WS kết nối: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<?,?> d = mapper.readValue(message.getPayload(), Map.class);
        String type = (String) d.get("type");
        String sid  = session.getId();

        switch (type != null ? type : "") {
            case "ping"          -> gui(session, Map.of("type","pong"));
            case "auth"          -> xuLyAuth(session, d);
            case "find_match"    -> xuLyFindMatch(session);
            case "cancel"        -> hangChoiDoi.remove(sid);
            case "create_room"   -> xuLyCreateRoom(session);
            case "join_room"     -> xuLyJoinRoom(session, d);
            case "start_room"    -> xuLyStartRoom(session, d);
            case "cancel_room"   -> xuLyCancelRoom(session, d);
            case "leave_room"    -> xuLyLeaveRoom(session);
            case "player_update" -> relayToRoom(session, d, sid);
            case "bullet_spawn"  -> relayToRoom(session, d, sid);
            case "hit_event"     -> relayToRoom(session, d, sid);
            case "game_over_report" -> xuLyGameOverReport(session, d);
            default -> log.debug("Message không xác định: {}", type);
        }
    }

    private void xuLyAuth(WebSocketSession session, Map<?,?> d) {
        String token = (String) d.get("token");
        if (token != null) {
            String username = authService.xacThucToken(token);
            if (username != null) sessionUsername.put(session.getId(), username);
        }
    }

    private void xuLyFindMatch(WebSocketSession session) throws IOException {
        String sid = session.getId();
        if (hangChoiDoi.contains(sid)) return;
        synchronized (hangChoiDoi) {
            if (!hangChoiDoi.isEmpty()) {
                String opp = hangChoiDoi.remove(0);
                WebSocketSession oppSess = sessions.get(opp);
                if (oppSess != null && oppSess.isOpen()) {
                    batDauMatchOnline(session, oppSess);
                    return;
                }
            }
            hangChoiDoi.add(sid);
            gui(session, Map.of("type","waiting","msg","Đang tìm đối thủ..."));
        }
    }

    private void batDauMatchOnline(WebSocketSession p1, WebSocketSession p2) throws IOException {
        String roomId = "match_" + System.currentTimeMillis();
        String sid1 = p1.getId(), sid2 = p2.getId();

        phongSessions.put(roomId, new ArrayList<>(List.of(sid1, sid2)));
        phongChoiMap.put(sid1, roomId);
        phongChoiMap.put(sid2, roomId);

        // Tạo bản đồ online (random)
        CauHinhAI cfg = CauHinhAI.builder().idAi(0).tenAi("ONLINE").cheDoChoi("online").build();
        QuanLyGame.PhongGame phong = quanLyGame.taoPhong(roomId, cfg, false);

        List<Map<String,Object>> players = List.of(
            Map.of("id",1,"x",80.0,"y",80.0,"hp",100),
            Map.of("id",2,"x",970.0,"y",540.0,"hp",100)
        );
        List<Map<String,Object>> obstacles = phong.getVatCans().stream().map(o ->
            Map.of("x",o.getX(),"y",o.getY(),"w",o.getRong(),"h",o.getCao())).toList();
        List<Map<String,Object>> items = phong.getItems().stream().map(it ->
            Map.of("type",it.getLoai(),"x",it.getX(),"y",it.getY())).toList();

        gui(p1, Map.of("type","game_start","myId",1,"roomId",roomId,"players",players,"obstacles",obstacles,"items",items));
        gui(p2, Map.of("type","game_start","myId",2,"roomId",roomId,"players",players,"obstacles",obstacles,"items",items));
    }

    private void xuLyCreateRoom(WebSocketSession session) throws IOException {
        String roomId = String.format("%04d", new Random().nextInt(10000));
        String sid    = session.getId();
        phongSessions.put(roomId, new ArrayList<>(List.of(sid)));
        phongChoiMap.put(sid, roomId);
        phongChuPhong.put(roomId, sid);
        gui(session, Map.of("type","room_created","roomId",roomId,"code",roomId));
    }

    private void xuLyJoinRoom(WebSocketSession session, Map<?,?> d) throws IOException {
        String code = (String) d.get("roomId");
        String sid  = session.getId();
        if (code==null||!phongSessions.containsKey(code)) {
            gui(session, Map.of("type","error","msg","Không tìm thấy phòng: "+code)); return;
        }
        List<String> members = phongSessions.get(code);
        if (members.size() >= 2) { gui(session, Map.of("type","error","msg","Phòng đã đầy")); return; }
        members.add(sid);
        phongChoiMap.put(sid, code);
        gui(session, Map.of("type","room_joined","roomId",code));
        // Notify chủ phòng
        String chuSid = phongChuPhong.get(code);
        WebSocketSession chuSess = chuSid != null ? sessions.get(chuSid) : null;
        if (chuSess!=null) gui(chuSess, Map.of("type","room_update","members",members.size()));
    }

    private void xuLyStartRoom(WebSocketSession session, Map<?,?> d) throws IOException {
        String roomId = (String) d.get("roomId");
        List<String> members = phongSessions.get(roomId);
        if (members==null||members.size()<2) { gui(session, Map.of("type","error","msg","Cần 2 người")); return; }
        WebSocketSession p1 = sessions.get(members.get(0));
        WebSocketSession p2 = sessions.get(members.get(1));
        if (p1!=null&&p2!=null) batDauMatchOnline(p1, p2);
    }

    private void xuLyCancelRoom(WebSocketSession session, Map<?,?> d) throws IOException {
        String roomId = (String) d.get("roomId");
        dongPhong(roomId);
    }

    private void xuLyLeaveRoom(WebSocketSession session) {
        String sid    = session.getId();
        String roomId = phongChoiMap.get(sid);
        if (roomId != null) {
            List<String> members = phongSessions.get(roomId);
            if (members != null) members.remove(sid);
            phongChoiMap.remove(sid);
        }
    }

    private void relayToRoom(WebSocketSession sender, Map<?,?> d, String senderSid) throws IOException {
        String roomId = phongChoiMap.get(senderSid);
        if (roomId == null) return;
        List<String> members = phongSessions.get(roomId);
        if (members == null) return;
        String payload = mapper.writeValueAsString(d);
        for (String sid : members) {
            if (sid.equals(senderSid)) continue;
            WebSocketSession s = sessions.get(sid);
            if (s != null && s.isOpen()) s.sendMessage(new TextMessage(payload));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sid = session.getId();
        sessions.remove(sid);
        hangChoiDoi.remove(sid);
        sessionUsername.remove(sid);
        String roomId = phongChoiMap.remove(sid);
        if (roomId != null) {
            List<String> members = phongSessions.get(roomId);
            if (members != null) members.remove(sid);
        }
        log.info("WS ngắt kết nối: {}", sid);
    }

    /**
     * Xử lý khi client báo game kết thúc.
     * Đợi 1 trong 2 client báo, sau đó gửi game_over cho cả phòng.
     * Dùng Set để chống xử lý 2 lần khi cả 2 client đều báo gần như cùng lúc.
     */
    private final Set<String> doneRooms = ConcurrentHashMap.newKeySet();

    private void xuLyGameOverReport(WebSocketSession session, Map<?,?> d) throws IOException {
        String sid    = session.getId();
        String roomId = phongChoiMap.get(sid);
        if (roomId == null) return;

        // Chỉ xử lý lần đầu tiên — bỏ qua báo cáo trùng
        if (!doneRooms.add(roomId)) return;

        Integer winner = d.get("winner") instanceof Number n ? n.intValue() : null;
        List<String> members = phongSessions.get(roomId);
        if (members == null) return;

        Map<String,Object> msg = new java.util.HashMap<>();
        msg.put("type", "game_over");
        if (winner != null) msg.put("winner", winner);

        for (String msid : members) {
            WebSocketSession s = sessions.get(msid);
            if (s != null && s.isOpen()) gui(s, msg);
        }

        // Dọn phòng sau 5s (cho client đủ thời gian nhận game_over)
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() {
                dongPhong(roomId);
                doneRooms.remove(roomId);
            }
        }, 5000);
    }

    private void dongPhong(String roomId) {
        List<String> members = phongSessions.remove(roomId);
        if (members != null) members.forEach(phongChoiMap::remove);
        phongChuPhong.remove(roomId);
        quanLyGame.xoaPhong(roomId);
    }

    private void gui(WebSocketSession s, Map<?,?> d) throws IOException {
        if (s != null && s.isOpen())
            s.sendMessage(new TextMessage(mapper.writeValueAsString(d)));
    }
}
