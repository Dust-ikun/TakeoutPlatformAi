package com.sky.chat.retrieve;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 在线检索（W1：纯向量召回 Top-K）
 * W8 将升级为 向量 + 关键词 双路召回 + RRF 融合；W9 加 Rerank
 */
@Service
public class RetrievalService {

    private final VectorStore vectorStore;

    public RetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public record RetrievedChunk(String text, double score,
                                 String type, String category, String title, String source) {
    }

    /**
     * @param topK     召回条数
     * @param docType  可选元数据过滤（dish/rule），null 则全库检索
     */
    public List<RetrievedChunk> search(String query, int topK, String docType) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.0); // W1 不过滤阈值，分数交给上层置信分档（W2）
        if (docType != null && !docType.isBlank()) {
            builder.filterExpression(new FilterExpressionBuilder()
                    .eq("type", docType).build());
        }
        List<Document> docs = vectorStore.similaritySearch(builder.build());
        return docs == null ? List.of() : docs.stream()
                .map(d -> new RetrievedChunk(
                        d.getText(),
                        d.getScore() == null ? 0.0 : d.getScore(),
                        str(d.getMetadata().get("type")),
                        str(d.getMetadata().get("category")),
                        str(d.getMetadata().get("title")),
                        str(d.getMetadata().get("source"))))
                .toList();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
