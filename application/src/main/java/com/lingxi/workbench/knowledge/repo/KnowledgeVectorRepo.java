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
     * pgvector 余弦相似度检索 SQL。
     *
     * <p>{@code embedding <=> ?::vector} 是 pgvector 余弦距离运算符，返回值 [0, 2]，
     * 0 表示完全相同。{@code 1 - distance} 将其转换为余弦相似度 [0, 1]，1 为完全匹配。
     * 子查询避免在 SELECT 和 WHERE 中重复计算距离表达式。</p>
     */
    private static final String SEARCH_SQL = """
            SELECT chunk_id, similarity
            FROM (
                SELECT
                    c.id AS chunk_id,
                    1 - (v.embedding <=> ?::vector) AS similarity
                FROM lingxi_vector.knowledge_chunk_vector v
                JOIN lingxi_core.ai_knowledge_chunk c ON c.id = v.chunk_id
                WHERE c.knowledge_base_id = ?
                  AND c.enabled = TRUE
            ) t
            WHERE similarity >= ?
            ORDER BY similarity DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 在指定知识库中检索与查询向量语义最相近的 Top-K Chunk。
     *
     * @param queryVector     查询文本的向量表示
     * @param knowledgeBaseId 目标知识库 ID
     * @param topK            返回条数
     * @param threshold       最低余弦相似度阈值（0~1）
     * @return 按相似度降序排列的 (chunkId, similarity)
     */
    public List<VectorSearchRow> search(float[] queryVector, Long knowledgeBaseId, int topK, double threshold) {
        String vectorLiteral = toVectorLiteral(queryVector);
        return jdbcTemplate.query(SEARCH_SQL,
                (rs, rowNum) -> new VectorSearchRow(
                        rs.getLong("chunk_id"),
                        rs.getDouble("similarity")
                ),
                vectorLiteral, knowledgeBaseId, threshold, topK);
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
