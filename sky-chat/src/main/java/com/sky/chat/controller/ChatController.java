package com.sky.chat.controller;

import com.sky.chat.qa.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 对话接口
 * - POST /chat        非流式（评测链路用）
 * - POST /chat/stream SSE 流式（W3）：meta → delta*N → done
 * conversationId 可选：传了则启用多轮会话记忆（滑动窗口）
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    public record ChatRequest(String message, String conversationId) {
    }

    @PostMapping
    public ChatService.ChatResult chat(@RequestBody ChatRequest req) {
        return chatService.ask(req.conversationId(), req.message());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest req) {
        return chatService.streamAsk(req.conversationId(), req.message());
    }
}
