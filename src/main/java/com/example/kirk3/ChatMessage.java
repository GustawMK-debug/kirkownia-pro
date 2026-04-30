package com.example.kirk3;

import jakarta.persistence.*;

@Entity
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sender;
    private String content;
    private String channel;
    private String timestamp; // Zmieniono na String, żeby Jackson się nie dławił!

    public ChatMessage() {}
    public ChatMessage(String sender, String content, String channel, String timestamp) {
        this.sender = sender;
        this.content = content;
        this.channel = channel;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public String getSender() { return sender; }
    public String getContent() { return content; }
    public String getChannel() { return channel; }
    public String getTimestamp() { return timestamp; }
}