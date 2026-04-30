package com.example.kirk3;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sender;
    private String content;
    private String channel;
    private LocalDateTime timestamp;

    public ChatMessage() {}
    public ChatMessage(String sender, String content, String channel, LocalDateTime timestamp) {
        this.sender = sender;
        this.content = content;
        this.channel = channel;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public String getSender() { return sender; }
    public String getContent() { return content; }
    public String getChannel() { return channel; }
    public LocalDateTime getTimestamp() { return timestamp; }
}