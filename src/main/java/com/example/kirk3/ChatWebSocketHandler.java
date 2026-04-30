package com.example.kirk3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Set<String> availableChannels = new CopyOnWriteArraySet<>(Set.of("ogolny", "gry", "pomoc"));
    private final UserRepository userRepository;
    private final ChatMessageRepository messageRepository;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatWebSocketHandler(UserRepository userRepository, ChatMessageRepository messageRepository, JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.mailSender = mailSender;
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
                String code = String.format("%06d", new Random().nextInt(999999));
                userRepository.save(new UserAccount(email, user, pass, code));
                sendEmail(email, code);
                session.sendMessage(new TextMessage("{\"type\":\"NEED_VERIFY\",\"username\":\"" + user + "\"}"));
            }
        } else if ("VERIFY".equals(type)) {
            String user = json.get("username").asText();
            String code = json.get("code").asText();
            userRepository.findByUsername(user).ifPresent(u -> {
                try {
                    if (u.getVerificationCode().equals(code)) {
                        u.setVerified(true);
                        userRepository.save(u);
                        session.sendMessage(new TextMessage("{\"type\":\"REGISTER_OK\"}"));
                    } else {
                        session.sendMessage(new TextMessage("{\"type\":\"ERROR\",\"message\":\"Błędny kod\"}"));
                    }
                } catch (Exception e) {}
            });
        } else if ("LOGIN".equals(type)) {
            String user = json.get("username").asText();
            String pass = json.get("password").asText();
            userRepository.findByUsername(user).filter(u -> u.getPasswordHash().equals(pass)).ifPresentOrElse(
                    u -> {
                        try {
                            if (!u.isVerified()) {
                                session.sendMessage(new TextMessage("{\"type\":\"NEED_VERIFY\",\"username\":\"" + user + "\"}"));
                                return;
                            }
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
                ChatMessage chatMsg = new ChatMessage(user, content, channel, LocalDateTime.now());
                messageRepository.save(chatMsg);
                String resp = objectMapper.writeValueAsString(Map.of("type","CHAT","sender",user,"content",content,"channel",channel,"timestamp",chatMsg.getTimestamp().toString()));
                for (WebSocketSession s : sessions.values()) if (s.isOpen()) s.sendMessage(new TextMessage(resp));
            }
        } else if ("JOIN_CHANNEL".equals(type)) {
            String ch = json.get("channel").asText();
            var hist = messageRepository.findByChannelOrderByTimestampAsc(ch);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of("type","HISTORY","channel",ch,"messages",hist))));
        } else if ("CREATE_CHANNEL".equals(type)) {
            String name = json.get("name").asText().toLowerCase().replaceAll("\\s+", "-");
            availableChannels.add(name);
            String upd = "{\"type\":\"CHANNELS_UPDATE\",\"channels\":" + objectMapper.writeValueAsString(availableChannels) + "}";
            for (WebSocketSession s : sessions.values()) if (s.isOpen()) s.sendMessage(new TextMessage(upd));
        }
    }

    private void sendEmail(String to, String code) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("Kod weryfikacyjny - Kirkownia PRO");
        msg.setText("Twój kod to: " + code);
        mailSender.send(msg);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
    }
}