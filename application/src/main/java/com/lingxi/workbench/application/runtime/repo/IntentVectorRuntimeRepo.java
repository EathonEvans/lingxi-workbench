package com.lingxi.workbench.application.runtime.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 在指定意图树版本、模型和维度范围内执行精确余弦召回。 */
@Repository
@RequiredArgsConstructor
public class IntentVectorRuntimeRepo {

    private static final String SEARCH_SQL = """
            SELECT intent_node_id, intent_code, MAX(similarity) AS similarity
              FROM (
                    SELECT intent_node_id,
                           intent_code,
                           1 - (embedding <=> ?::vector) AS similarity
                      FROM lingxi_vector.intent_tree_vector
                     WHERE intent_tree_version_id = ?
                       AND embedding_model_instance_id = ?
                       AND embedding_dimension = ?
                   ) candidates
             GROUP BY intent_node_id, intent_code
             ORDER BY similarity DESC, intent_code ASC
             LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<IntentCandidateRow> search(
            float[] queryVector,
            Long intentTreeVersionId,
            Long embeddingModelInstanceId,
            int topK) {
        return jdbcTemplate.query(
                SEARCH_SQL,
                (resultSet, rowNum) -> new IntentCandidateRow(
                        resultSet.getString("intent_node_id"),
                        resultSet.getString("intent_code"),
                        resultSet.getDouble("similarity")),
                toVectorLiteral(queryVector),
                intentTreeVersionId,
                embeddingModelInstanceId,
                queryVector.length,
                topK);
    }

    private static String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 10).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(vector[index]);
        }
        return builder.append(']').toString();
    }

    /** @param similarity 叶子全部原型和正例向量中的最高相似度 */
    public record IntentCandidateRow(String intentNodeId, String intentCode, double similarity) {
    }
}
