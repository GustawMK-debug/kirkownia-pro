package com.example.kirk3;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final Map<WebSocketSession, String> userNames = new ConcurrentHashMap<>();
    private static final Map<WebSocketSession, String> userChannels = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ChatMessageRepository messageRepository, UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        if (payload.startsWith("REGISTER|")) {
            String[] parts = payload.split("\\|");
            if (parts.length < 4) return;
            String email = parts[1];
            String username = parts[2];
            String password = parts[3];

            if (userRepository.findByEmail(email) != null) {
                session.sendMessage(new TextMessage("AUTH_ERROR|Konto z tym adresem email już istnieje!"));
                return;
            }
            if (userRepository.findByUsername(username) != null) {
                session.sendMessage(new TextMessage("AUTH_ERROR|Kryptonim (nick) zajęty!"));
                return;
            }

            userRepository.save(new UserAccount(email, username, passwordEncoder.encode(password)));
            session.sendMessage(new TextMessage("REGISTER_SUCCESS|Konto utworzone. Możesz się zalogować."));
            return;
        }

        if (payload.startsWith("LOGIN|")) {
            String[] parts = payload.split("\\|");
            if (parts.length < 3) return;
            String email = parts[1];
            String password = parts[2];

            UserAccount user = userRepository.findByEmail(email);
            if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
                session.sendMessage(new TextMessage("AUTH_ERROR|Błędny email lub hasło!"));
                return;
            }

            if (userNames.containsValue(user.getUsername())) {
                session.sendMessage(new TextMessage("AUTH_ERROR|Agent już aktywny na serwerze!"));
                return;
            }

            userNames.put(session, user.getUsername());
            userChannels.put(session, "ogólny");
            session.sendMessage(new TextMessage("LOGIN_SUCCESS|" + user.getUsername()));
            sendHistory(session, "ogólny");
            broadcastUserList();
            return;
        }

        if (!userNames.containsKey(session)) return;

        if (payload.startsWith("MOVE|")) {
            String newChan = payload.split("\\|")[1];
            userChannels.put(session, newChan);
            sendHistory(session, newChan);
            broadcastUserList();
            return;
        }

        if (payload.startsWith("STATUS|")) {
            broadcastToAll(payload);
            return;
        }

        String chan = userChannels.getOrDefault(session, "ogólny");
        String sender = userNames.get(session);
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        messageRepository.save(new ChatMessage(sender, payload, chan));
        broadcastToAll("TEXT|" + chan + "|" + sender + ":" + payload + "|" + time);
    }

    private void sendHistory(WebSocketSession session, String channel) throws IOException {
        List<ChatMessage> history = messageRepository.findByChannelOrderByTimestampAsc(channel);
        for (ChatMessage msg : history) {
            String formatted = "TEXT|" + channel + "|" + msg.getSender() + ":" + msg.getContent() + "|" + msg.getFormattedTime();
            session.sendMessage(new TextMessage(formatted));
        }
    }

    private void broadcastToAll(String finalPayload) throws IOException {
        TextMessage out = new TextMessage(finalPayload);
        for (WebSocketSession s : userNames.keySet()) {
            if (s.isOpen()) s.sendMessage(out);
        }
    }

    private void broadcastUserList() throws Exception {
        String data = userNames.entrySet().stream()
                .map(e -> e.getValue() + ":" + userChannels.getOrDefault(e.getKey(), "ogólny"))
                .collect(Collectors.joining(","));
        broadcastToAll("USERS|" + data);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        userNames.remove(session);
        userChannels.remove(session);
        broadcastUserList();
    }
}