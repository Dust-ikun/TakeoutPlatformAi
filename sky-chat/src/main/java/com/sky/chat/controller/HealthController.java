package com.sky.chat.controller;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 骨架验收用：验证 DashScope Embedding 链路与 PGVector 可用性
 */
@RestController
@RequestMapping("/chat")
public class HealthController {

    private final EmbeddingModel embeddingModel;

    public HealthController(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "sky-chat");
    }

    /** 冒烟：对固定文本做一次 embedding，返回向量维度，证明 Key/模型/维度配置全通 */
    @GetMapping("/embedding-check")
    public Map<String, Object> embeddingCheck() {
        EmbeddingResponse resp = embeddingModel.embedForResponse(java.util.List.of("辣子鸡"));
        float[] vec = resp.getResults().get(0).getOutput();
        return Map.of("dimensions", vec.length);
    }
}
