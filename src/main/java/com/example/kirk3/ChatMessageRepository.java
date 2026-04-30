package com.example.kirk3;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // Sortujemy po ID, dzięki czemu historia zawsze wraca w idealnej kolejności
    List<ChatMessage> findByChannelOrderByIdAsc(String channel);
}