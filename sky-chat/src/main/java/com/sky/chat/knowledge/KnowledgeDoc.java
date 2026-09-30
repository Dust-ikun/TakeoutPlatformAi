package com.sky.chat.knowledge;

import lombok.Data;

/**
 * 知识文档导入记录（metadata 表，向量本体在 vector_store）
 */
@Data
public class KnowledgeDoc {

    private Long id;
    private String docName;
    private String docType;      // dish | rule
    private String fingerprint;  // 内容 SHA-256
    private String status;       // PROCESSING | SUCCESS | FAILED
    private Integer chunkCount;
    private String vectorIds;    // JSON 数组
    private String errorMsg;

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
}
