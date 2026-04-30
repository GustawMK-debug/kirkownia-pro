package com.example.kirk3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> userNames = new ConcurrentHashMap<>();
    private final Map<String, String> userVoiceChannels = new ConcurrentHashMap<>();
    private final Set<String> textChannels = new CopyOnWriteArraySet<>(Set.of("ogolny", "projekty", "off-topic"));
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatWebSocketHandler(ChatMessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode json = mapper.readTree(message.getPayload());
        String type = json.get("type").asText();

        if ("LOGIN".equals(type)) {
            String user = json.get("username").asText();
            userNames.put(session.getId(), user);
            session.sendMessage(new TextMessage("{\"type\":\"LOGIN_OK\",\"username\":\"" + user + "\",\"channels\":" + mapper.writeValueAsString(textChannels) + "}"));
            broadcastVoiceState();
        } else if ("CHAT".equals(type)) {
            String user = userNames.get(session.getId());
            if (user != null) {
                String content = json.get("content").asText();
                String channel = json.get("channel").asText();
                ChatMessage chatMsg = new ChatMessage(user, content, channel, LocalDateTime.now());
                messageRepository.save(chatMsg);
                broadcast(mapper.writeValueAsString(Map.of(
                        "type", "CHAT", "sender", user, "content", content,
                        "channel", channel, "timestamp", chatMsg.getTimestamp().toString()
                )));
            }
        } else if ("JOIN_CHANNEL".equals(type)) {
            String ch = json.get("channel").asText();
            var hist = messageRepository.findByChannelOrderByTimestampAsc(ch);
            session.sendMessage(new TextMessage(mapper.writeValueAsString(Map.of(
                    "type", "HISTORY", "channel", ch, "messages", hist
            ))));
        } else if ("VC_JOIN".equals(type)) {
            userVoiceChannels.put(session.getId(), json.get("vcName").asText());
            broadcastVoiceState();
        } else if ("VC_LEAVE".equals(type)) {
            userVoiceChannels.remove(session.getId());
            broadcastVoiceState();
        } else if ("VC_STATE".equals(type)) {
            broadcast(message.getPayload());
        } else if ("SIGNAL".equals(type)) {
            broadcast(message.getPayload());
        }
    }

    private void broadcastVoiceState() throws Exception {
        Map<String, Map<String, String>> voiceData = new ConcurrentHashMap<>();
        userVoiceChannels.forEach((sid, vc) -> {
            String name = userNames.get(sid);
            if (name != null) {
                voiceData.computeIfAbsent(vc, k -> new ConcurrentHashMap<>()).put(sid, name);
            }
        });
        broadcast(mapper.writeValueAsString(Map.of("type", "VC_UPDATE", "data", voiceData)));
    }

    private void broadcast(String msg) throws Exception {
        for (WebSocketSession s : sessions.values()) {
            if (s.isOpen()) s.sendMessage(new TextMessage(msg));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        userNames.remove(session.getId());
        userVoiceChannels.remove(session.getId());
        broadcastVoiceState(); // To aktualizuje listę u innych, gdy ktoś zamknie kartę!
    }
}