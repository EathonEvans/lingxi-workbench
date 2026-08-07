package com.lingxi.workbench.chat.memory.repo;

import com.lingxi.workbench.chat.memory.model.entity.InteractionRecordEntity;
import com.lingxi.workbench.chat.memory.model.entity.InteractionRecordVectorEntity;
import com.lingxi.workbench.chat.memory.repo.mapper.InteractionRecordVectorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 交互记录向量索引仓库，使用原生 SQL 操作 pgvector 余弦距离检索。 */
@Repository
@RequiredArgsConstructor
public class InteractionRecordVectorRepo {
    private final InteractionRecordVectorMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    /** 插入向量。 */
    public void insert(Long recordId, Long embeddingModelInstanceId, float[] embedding) {
        InteractionRecordVectorEntity entity = new InteractionRecordVectorEntity();
        entity.setRecordId(recordId);
        entity.setEmbeddingModelInstanceId(embeddingModelInstanceId);
        entity.setEmbeddingDimension(embedding.length);
        entity.setEmbedding(embedding);
        mapper.insert(entity);
    }

    /**
     * 按余弦距离检索与 queryVector 最相似的 topK 条交互记录。
     * 只检索当前用户的记录，通过子查询关联 ai_interaction_record。
     */
    @SuppressWarnings("DataFlowIssue")
    public List<InteractionRecordEntity> findSimilar(Long userId, Long embeddingModelInstanceId, float[] queryVector,
                                                     int retentionDays, int topK) {
        String vectorStr = vectorToString(queryVector);
        return jdbcTemplate.query(
                "SELECT r.* FROM lingxi_core.ai_interaction_record r " +
                "JOIN lingxi_vector.interaction_record_vector v ON v.record_id = r.id " +
                "WHERE r.user_id = ? " +
                "AND v.embedding_model_instance_id = ? " +
                "AND v.embedding_dimension = ? " +
                "AND r.created_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 day') " +
                "ORDER BY v.embedding <=> ?::vector " +
                "LIMIT ?",
                (rs, rowNum) -> {
                    InteractionRecordEntity entity = new InteractionRecordEntity();
                    entity.setId(rs.getLong("id"));
                    entity.setUserId(rs.getLong("user_id"));
                    entity.setConversationId(rs.getObject("conversation_id", Long.class));
                    entity.setSummary(rs.getString("summary"));
                    entity.setEntities(null);  // 检索时不加载 entities JSONB
                    entity.setCreatedAt(rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC));
                    return entity;
                },
                userId, embeddingModelInstanceId, queryVector.length, retentionDays, vectorStr, topK);
    }

    private static String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
