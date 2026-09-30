package com.sky.chat.controller;

import com.sky.chat.knowledge.KnowledgeDoc;
import com.sky.chat.knowledge.KnowledgeDocDao;
import com.sky.chat.knowledge.KnowledgeIngestService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口
 * W1 为同步入库（请求内完成解析→分块→嵌入→入库）；
 * W10 将改为"写待处理表 + 发 MQ 即返"，本接口契约保持不变
 */
@RestController
@RequestMapping("/admin/knowledge")
public class AdminKnowledgeController {

    private final KnowledgeIngestService ingestService;
    private final KnowledgeDocDao docDao;

    public AdminKnowledgeController(KnowledgeIngestService ingestService, KnowledgeDocDao docDao) {
        this.ingestService = ingestService;
        this.docDao = docDao;
    }

    public record UploadRequest(String docName, String docType, String content) {
    }

    @PostMapping
    public Map<String, Object> upload(@RequestBody UploadRequest req) {
        KnowledgeDoc doc = ingestService.ingest(req.docName(), req.docType(), req.content());
        return Map.of("docId", doc.getId(), "docName", doc.getDocName(),
                "chunks", doc.getChunkCount(), "status", doc.getStatus());
    }

    @GetMapping("/{id}")
    public ResponseEntity<KnowledgeDoc> status(@PathVariable Long id) {
        return docDao.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/list")
    public List<KnowledgeDoc> list() {
        return docDao.findAll();
    }

    /**
     * 一键导入内置种子知识（dishes.md / rules.md），fingerprint 幂等，可重复调用
     */
    @PostMapping("/seed")
    public Map<String, Object> seed() throws Exception {
        int dishes = ingestClasspath("knowledge/dishes.md", "dish");
        int rules = ingestClasspath("knowledge/rules.md", "rule");
        return Map.of("dishChunks", dishes, "ruleChunks", rules);
    }

    private int ingestClasspath(String classpath, String type) throws Exception {
        try (var in = new ClassPathResource(classpath).getInputStream()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String docName = classpath.substring(classpath.lastIndexOf('/') + 1);
            return ingestService.ingest(docName, type, content).getChunkCount();
        }
    }
}
