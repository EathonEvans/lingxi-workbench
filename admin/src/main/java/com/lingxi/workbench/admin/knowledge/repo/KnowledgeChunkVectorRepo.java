package com.lingxi.workbench.admin.knowledge.repo;

import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * pgvector 知识库 Chunk 向量数据仓库。
 *
 * <p>向量存储在独立的 lingxi_vector Schema 中，通过 Chunk ID 逻辑关联。
 * 所有写入使用 ON CONFLICT 实现幂等 Upsert。</p>
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeChunkVectorRepo {
    private static final String INSERT_SQL = """
            INSERT INTO lingxi_vector.knowledge_chunk_vector
                (chunk_id, content, embedding)
            VALUES (?, ?, ?::vector)
            ON CONFLICT (chunk_id) DO UPDATE SET
                content = EXCLUDED.content,
                embedding = EXCLUDED.embedding
            """;

    private final JdbcTemplate jdbcTemplate;

    /** 按处理任务删除派生向量，必须在删除对应 Chunk 之前调用。 */
    public void deleteByTaskId(Long taskId) {
        jdbcTemplate.update("""
                DELETE FROM lingxi_vector.knowledge_chunk_vector
                 WHERE chunk_id IN (
                     SELECT id FROM lingxi_core.ai_knowledge_chunk WHERE task_id = ?
                 )
                """, taskId);
    }

    /** 按文档删除全部派生向量，必须在删除对应 Chunk 之前调用。 */
    public void deleteByDocumentId(Long documentId) {
        jdbcTemplate.update("""
                DELETE FROM lingxi_vector.knowledge_chunk_vector
                 WHERE chunk_id IN (
                     SELECT id FROM lingxi_core.ai_knowledge_chunk WHERE document_id = ?
                 )
                """, documentId);
    }

    /** 按批次幂等写入向量，控制单次模型请求和数据库事务大小。 */
    public void upsertBatch(List<KnowledgeChunkEntity> chunks, List<float[]> vectors) {
        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("Chunk数量与向量数量不一致");
        }
        List<VectorRow> rows = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            rows.add(new VectorRow(chunks.get(index), vectors.get(index)));
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 100, (statement, row) -> {
            KnowledgeChunkEntity chunk = row.chunk();
            float[] vector = row.vector();
            statement.setLong(1, chunk.getId());
            statement.setString(2, chunk.getContent());
            statement.setString(3, toVectorLiteral(vector));
        });
    }

    /** 更新单个 Chunk 对应的正文和向量，人工修订后调用。 */
    public void upsert(KnowledgeChunkEntity chunk, float[] vector) {
        upsertBatch(List.of(chunk), List.of(vector));
    }

    /** 校验冻结任务的向量维度一致，并返回该索引版本使用的维度。 */
    public int findConsistentDimension(List<Long> taskIds, int expectedVectorCount) {
        if (taskIds.isEmpty()) {
            throw new IllegalArgumentException("知识索引至少需要一个分块任务");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(taskIds.size(), "?"));
        String sql = """
                SELECT MIN(vector_dims(vector.embedding)) AS min_dimension,
                       MAX(vector_dims(vector.embedding)) AS max_dimension,
                       COUNT(*) AS vector_count
                  FROM lingxi_vector.knowledge_chunk_vector vector
                  JOIN lingxi_core.ai_knowledge_chunk chunk ON chunk.id = vector.chunk_id
                 WHERE chunk.task_id IN (%s)
                   AND chunk.enabled = TRUE
                """.formatted(placeholders);
        Map<String, Object> result = jdbcTemplate.queryForMap(sql, taskIds.toArray());
        Number count = (Number) result.get("vector_count");
        Number minimum = (Number) result.get("min_dimension");
        Number maximum = (Number) result.get("max_dimension");
        if (count.intValue() != expectedVectorCount || minimum == null || maximum == null || minimum.intValue() != maximum.intValue()) {
            throw new IllegalStateException("知识索引向量缺失或维度不一致");
        }
        return minimum.intValue();
    }

    /** 将 float 数组转换为 pgvector 字面量格式 [v1,v2,...]。 */
    private static String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 10).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) builder.append(',');
            builder.append(vector[index]);
        }
        return builder.append(']').toString();
    }

    private record VectorRow(KnowledgeChunkEntity chunk, float[] vector) {
    }
}
