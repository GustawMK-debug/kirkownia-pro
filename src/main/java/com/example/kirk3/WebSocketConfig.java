package com.example.kirk3;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;

    public WebSocketConfig(ChatMessageRepository m, UserRepository u) {
        this.messageRepository = m;
        this.userRepository = u;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new ChatWebSocketHandler(messageRepository, userRepository), "/chat").setAllowedOrigins("*");
    }
}