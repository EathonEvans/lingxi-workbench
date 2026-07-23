package com.lingxi.workbench.admin.knowledge.repo;

import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeChunkEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/** pgvector知识库Chunk向量数据仓库。 */
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

    /** 按处理任务删除派生向量，必须在删除对应Chunk之前调用。 */
    public void deleteByTaskId(Long taskId) {
        jdbcTemplate.update("""
                DELETE FROM lingxi_vector.knowledge_chunk_vector
                 WHERE chunk_id IN (
                     SELECT id FROM lingxi_core.ai_knowledge_chunk WHERE task_id = ?
                 )
                """, taskId);
    }

    /** 按文档删除全部派生向量，必须在删除对应Chunk之前调用。 */
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

    /** 更新单个Chunk对应的正文和向量。 */
    public void upsert(KnowledgeChunkEntity chunk, float[] vector) {
        upsertBatch(List.of(chunk), List.of(vector));
    }

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
