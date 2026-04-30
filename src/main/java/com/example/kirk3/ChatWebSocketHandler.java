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
    private final Set<String> availableChannels = new CopyOnWriteArraySet<>(Set.of("ogolny", "gry", "pomoc"));
    private final UserRepository userRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatWebSocketHandler(UserRepository userRepository, ChatMessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode json = objectMapper.readTree(message.getPayload());
        String type = json.get("type").asText();

        if ("REGISTER".equals(type)) {
            String email = json.get("email").asText();
            String user = json.get("username").asText();
            String pass = json.get("password").asText();
            if (userRepository.findByUsername(user).isPresent()) {
                session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Użytkownik istnieje\"}"));
            } else {
                userRepository.save(new UserAccount(email, user, pass));
                session.sendMessage(new TextMessage("{\"type\":\"REGISTER_OK\"}"));
            }
        } else if ("LOGIN".equals(type)) {
            String user = json.get("username").asText();
            String pass = json.get("password").asText();
            userRepository.findByUsername(user).filter(u -> u.getPasswordHash().equals(pass)).ifPresentOrElse(
                    u -> {
                        try {
                            session.getAttributes().put("user", user);
                            session.sendMessage(new TextMessage("{\"type\":\"LOGIN_OK\",\"username\":\"" + user + "\",\"channels\":" + objectMapper.writeValueAsString(availableChannels) + "}"));
                        } catch (Exception e) {}
                    },
                    () -> {
                        try { session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Błędne dane\"}")); } catch (Exception e) {}
                    }
            );
        } else if ("CHAT".equals(type)) {
            String user = (String) session.getAttributes().get("user");
            if (user != null) {
                String content = json.get("content").asText();
                String channel = json.get("channel").asText();
                ChatMessage chatMessage = new ChatMessage(user, content, channel, LocalDateTime.now());
                messageRepository.save(chatMessage);

                String response = objectMapper.writeValueAsString(Map.of(
                        "type", "CHAT",
                        "sender", user,
                        "content", content,
                        "channel", channel,
                        "timestamp", chatMessage.getTimestamp().toString()
                ));

                for (WebSocketSession s : sessions.values()) {
                    if (s.isOpen()) s.sendMessage(new TextMessage(response));
                }
            }
        } else if ("JOIN_CHANNEL".equals(type)) {
            String channel = json.get("channel").asText();
            var history = messageRepository.findByChannelOrderByTimestampAsc(channel);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                    "type", "HISTORY",
                    "channel", channel,
                    "messages", history
            ))));
        } else if ("CREATE_CHANNEL".equals(type)) {
            String newChannel = json.get("name").asText().toLowerCase().replaceAll("\\s+", "-");
            availableChannels.add(newChannel);
            String update = "{\"type\":\"CHANNELS_UPDATE\",\"channels\":" + objectMapper.writeValueAsString(availableChannels) + "}";
            for (WebSocketSession s : sessions.values()) {
                if (s.isOpen()) s.sendMessage(new TextMessage(update));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
    }
}