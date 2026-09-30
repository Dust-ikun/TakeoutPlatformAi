package com.sky.chat.controller;

import com.sky.chat.retrieve.RetrievalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 检索调试/评测用接口（W2 评测集跑分也走这条链路）
 */
@RestController
@RequestMapping("/admin/eval")
public class EvalController {

    private final RetrievalService retrievalService;

    public EvalController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @GetMapping("/search")
    public List<RetrievalService.RetrievedChunk> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(required = false) String docType) {
        return retrievalService.search(q, topK, docType);
    }
}
