package com.sky.chat.controller;

import com.sky.chat.eval.EvalService;
import com.sky.chat.retrieve.RetrievalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 检索调试 / 评测跑分接口
 * - GET  /admin/eval/set  查看评测集
 * - POST /admin/eval/run?mode=retrieval|full[&category=dish_exact]  跑分
 *   retrieval：秒级（不调 LLM），用于调阈值；full：完整问答（30 条约 1-2 分钟）
 */
@RestController
@RequestMapping("/admin/eval")
public class EvalController {

    private final RetrievalService retrievalService;
    private final EvalService evalService;

    public EvalController(RetrievalService retrievalService, EvalService evalService) {
        this.retrievalService = retrievalService;
        this.evalService = evalService;
    }

    @GetMapping("/search")
    public List<RetrievalService.RetrievedChunk> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(required = false) String docType) {
        return retrievalService.search(q, topK, docType);
    }

    @GetMapping("/set")
    public List<EvalService.EvalCase> evalSet() {
        return evalService.getCases();
    }

    @PostMapping("/run")
    public EvalService.EvalReport run(
            @RequestParam(defaultValue = "retrieval") String mode,
            @RequestParam(required = false) String category) {
        return evalService.run(mode, category);
    }
}
