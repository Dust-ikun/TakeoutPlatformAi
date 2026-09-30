package com.sky.chat.knowledge;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgeDocDao {

    private final JdbcClient jdbc;

    public KnowledgeDocDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<KnowledgeDoc> findByName(String docName) {
        return jdbc.sql("SELECT * FROM knowledge_doc WHERE doc_name = :n")
                .param("n", docName)
                .query(KnowledgeDoc.class)
                .optional();
    }

    public Optional<KnowledgeDoc> findById(Long id) {
        return jdbc.sql("SELECT * FROM knowledge_doc WHERE id = :id")
                .param("id", id)
                .query(KnowledgeDoc.class)
                .optional();
    }

    public List<KnowledgeDoc> findAll() {
        return jdbc.sql("SELECT * FROM knowledge_doc ORDER BY id")
                .query(KnowledgeDoc.class)
                .list();
    }

    public void insert(KnowledgeDoc d) {
        jdbc.sql("""
                INSERT INTO knowledge_doc
                (doc_name, doc_type, fingerprint, status, chunk_count, vector_ids, error_msg)
                VALUES (:n, :t, :f, :s, :c, :v, :e)
                """)
                .param("n", d.getDocName()).param("t", d.getDocType())
                .param("f", d.getFingerprint()).param("s", d.getStatus())
                .param("c", d.getChunkCount()).param("v", d.getVectorIds())
                .param("e", d.getErrorMsg())
                .update();
    }

    public void updateImport(KnowledgeDoc d) {
        jdbc.sql("""
                UPDATE knowledge_doc
                SET fingerprint = :f, status = :s, chunk_count = :c,
                    vector_ids = :v, error_msg = :e, doc_type = :t,
                    updated_at = now()
                WHERE doc_name = :n
                """)
                .param("f", d.getFingerprint()).param("s", d.getStatus())
                .param("c", d.getChunkCount()).param("v", d.getVectorIds())
                .param("e", d.getErrorMsg()).param("t", d.getDocType())
                .param("n", d.getDocName())
                .update();
    }
}
