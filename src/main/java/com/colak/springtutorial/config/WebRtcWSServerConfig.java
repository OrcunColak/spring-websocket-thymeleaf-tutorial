package com.colak.springtutorial.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint(value = "/webrtc/{username}")

@Slf4j
public class WebRtcWSServerConfig {

    private static final Map<String, Session> sessionMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("username") String username, @PathParam("publicKey") String publicKey) {
        sessionMap.put(username, session);
    }

    @OnClose
    public void onClose(Session session) {
        for (Map.Entry<String, Session> entry : sessionMap.entrySet()) {
            if (entry.getValue() == session) {
                sessionMap.remove(entry.getKey());
                break;
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("onError", error);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            // jackson
            ObjectMapper mapper = new ObjectMapper();
            mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            HashMap hashMap = mapper.readValue(message, HashMap.class);
            String type = (String) hashMap.get("type");
            // to user
            String toUser = (String) hashMap.get("toUser");
            Session toUserSession = sessionMap.get(toUser);
            String fromUser = (String) hashMap.get("fromUser");
            // msg
            String msg = (String) hashMap.get("msg");
            // sdp
            String sdp = (String) hashMap.get("sdp");
            // ice
            Map iceCandidate = (Map) hashMap.get("iceCandidate");
            HashMap<String, Object> map = new HashMap<>();
            map.put("type", type);
            if (toUserSession == null) {
                toUserSession = session;
                map.put("type", "call_back");
                map.put("fromUser", "system message");
                map.put("msg", "Sorry，not online！");
                send(toUserSession, mapper.writeValueAsString(map));
                return;
            }
            if ("hangup".equals(type)) {
                map.put("fromUser", fromUser);
                map.put("msg", "close！");
            }
            if ("call_start".equals(type)) {
                map.put("fromUser", fromUser);
                map.put("msg", "1");
            }

            if ("call_back".equals(type)) {
                map.put("fromUser", toUser);
                map.put("msg", msg);
            }
            // offer
            if ("offer".equals(type)) {
                map.put("fromUser", toUser);
                map.put("sdp", sdp);
            }
            // answer
            if ("answer".equals(type)) {
                map.put("fromUser", toUser);
                map.put("sdp", sdp);
            }
            // ice
            if ("_ice".equals(type)) {
                map.put("fromUser", toUser);
                map.put("iceCandidate", iceCandidate);
            }
            send(toUserSession, mapper.writeValueAsString(map));
        } catch (Exception exception) {
            log.error("Exception", exception);
        }
    }

    private void send(Session session, String message) {
        try {
            log.info(message);
            session.getBasicRemote().sendText(message);
        } catch (Exception exception) {
            log.error("Exception", exception);
        }
    }
}
