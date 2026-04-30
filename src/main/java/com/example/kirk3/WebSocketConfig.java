package com.example.kirk3;

import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final JavaMailSender mailSender;

    public WebSocketConfig(UserRepository userRepository, ChatMessageRepository chatMessageRepository, JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.mailSender = mailSender;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new ChatWebSocketHandler(userRepository, chatMessageRepository, mailSender), "/chat")
                .setAllowedOrigins("*");
    }
}