package com.sky.chat.controller;

import com.sky.chat.qa.ChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话接口（W1 非流式；W5 升级 POST /chat/stream SSE）
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    public record ChatRequest(String message) {
    }

    @PostMapping
    public ChatService.ChatResult chat(@RequestBody ChatRequest req) {
        return chatService.ask(req.message());
    }
}
