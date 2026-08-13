package com.lingxi.workbench.knowledge.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * pgvector 向量相似度检索数据仓库。
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeVectorRepo {

    /**
     * 版本化 pgvector 余弦相似度检索 SQL。
     *
     * <p>{@code embedding <=> ?::vector} 是 pgvector 余弦距离运算符，返回值 [0, 2]，
     * 0 表示完全相同。{@code 1 - distance} 将其转换为余弦相似度 [0, 1]，1 为完全匹配。
     * 子查询避免在 SELECT 和 WHERE 中重复计算距离表达式。
     * 检索范围限定在发布索引版本冻结的分块任务集合，且按模型实例过滤，防止跨模型维度混检。</p>
     */
    private static final String VERSION_SEARCH_SQL = """
            SELECT chunk_id, similarity
            FROM (
                SELECT
                    chunk.id AS chunk_id,
                    1 - (vector.embedding <=> ?::vector) AS similarity
                FROM lingxi_core.ai_knowledge_index_version version
                JOIN lingxi_core.ai_knowledge_index_version_task reference
                  ON reference.index_version_id = version.id
                JOIN lingxi_core.ai_knowledge_chunk chunk
                  ON chunk.task_id = reference.chunk_task_id
                JOIN lingxi_vector.knowledge_chunk_vector vector
                  ON vector.chunk_id = chunk.id
                WHERE version.id = ?
                  AND version.knowledge_base_id = ?
                  AND version.embedding_model_instance_id = ?
                  AND chunk.knowledge_base_id = ?
                  AND chunk.enabled = TRUE
            ) t
            WHERE similarity >= ?
            ORDER BY similarity DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    /** 仅在发布版本冻结的分块任务集合中执行相似度检索。 */
    public List<VectorSearchRow> searchVersion(float[] queryVector, Long knowledgeBaseId, Long indexVersionId, Long embeddingModelInstanceId, int topK, double threshold) {
        String vectorLiteral = toVectorLiteral(queryVector);
        return jdbcTemplate.query(VERSION_SEARCH_SQL,
                (resultSet, rowNumber) -> new VectorSearchRow(
                        resultSet.getLong("chunk_id"), resultSet.getDouble("similarity")),
                vectorLiteral, indexVersionId, knowledgeBaseId, embeddingModelInstanceId,
                knowledgeBaseId, threshold, topK);
    }

    private static String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }

    /**
     * 向量检索结果行。
     *
     * @param chunkId    匹配到的 Chunk ID
     * @param similarity pgvector 计算出的余弦相似度（0~1），越高越相关
     */
    public record VectorSearchRow(Long chunkId, Double similarity) {
    }
}
