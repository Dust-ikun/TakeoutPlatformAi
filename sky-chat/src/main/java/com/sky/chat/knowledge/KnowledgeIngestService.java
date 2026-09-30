package com.sky.chat.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 知识入库服务（W1 同步链路；W10 将解析/分块/嵌入搬到 MQ Worker，接口语义不变）
 *
 * 幂等设计：每篇文档按内容 SHA-256 生成 fingerprint——
 * 1. 首次导入：写 vector_store + 登记文档记录
 * 2. 重复导入（同名文档）：内容没变 → 直接跳过；内容变了 → 先按上次的
 *    vector_ids 删除旧 chunk，再写入新 chunk（防重复 chunk 污染检索）
 */
@Service
public class KnowledgeIngestService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestService.class);

    private final KnowledgeChunker chunker;
    private final VectorStore vectorStore;
    private final KnowledgeDocDao docDao;
    private final ObjectMapper objectMapper;

    public KnowledgeIngestService(KnowledgeChunker chunker, VectorStore vectorStore,
                                  KnowledgeDocDao docDao, ObjectMapper objectMapper) {
        this.chunker = chunker;
        this.vectorStore = vectorStore;
        this.docDao = docDao;
        this.objectMapper = objectMapper;
    }

    public record IngestResult(Long docId, String docName, int chunkCount, boolean skipped) {
    }

    public KnowledgeDoc ingest(String docName, String docType, String content) {
        String fingerprint = sha256(content);
        KnowledgeDoc existing = docDao.findByName(docName).orElse(null);

        if (existing != null && fingerprint.equals(existing.getFingerprint())
                && KnowledgeDoc.STATUS_SUCCESS.equals(existing.getStatus())) {
            log.info("[ingest] {} 内容未变化(fingerprint 命中)，跳过", docName);
            return existing;
        }

        List<KnowledgeChunker.Chunk> chunks = chunker.chunk(docType, docName, content);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("文档无可识别的 ## 小节: " + docName);
        }

        // 内容有更新：先删旧 chunk（按上次登记的向量 id），保证"先删旧再写新"
        if (existing != null && existing.getVectorIds() != null) {
            List<String> oldIds = parseIds(existing.getVectorIds());
            if (!oldIds.isEmpty()) {
                vectorStore.delete(oldIds);
                log.info("[ingest] {} 删除旧 chunk {} 条", docName, oldIds.size());
            }
        }

        // 生成向量 id 并写入（metadata 附 fingerprint，便于追溯）
        List<String> vectorIds = new ArrayList<>(chunks.size());
        List<Document> documents = new ArrayList<>(chunks.size());
        for (KnowledgeChunker.Chunk c : chunks) {
            String id = UUID.randomUUID().toString();
            vectorIds.add(id);
            documents.add(Document.builder()
                    .id(id)
                    .text(c.text())
                    .metadata(c.metadata())
                    .build());
        }
        vectorStore.add(documents);

        KnowledgeDoc doc = existing != null ? existing : new KnowledgeDoc();
        doc.setDocName(docName);
        doc.setDocType(docType);
        doc.setFingerprint(fingerprint);
        doc.setStatus(KnowledgeDoc.STATUS_SUCCESS);
        doc.setChunkCount(chunks.size());
        doc.setVectorIds(toJson(vectorIds));
        doc.setErrorMsg(null);
        if (existing == null) {
            docDao.insert(doc);
        } else {
            docDao.updateImport(doc);
        }
        log.info("[ingest] {} 入库成功，{} chunks", docName, chunks.size());
        return doc;
    }

    static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String toJson(List<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> parseIds(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
