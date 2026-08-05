package com.lingxi.workbench.admin.intent.repo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 意图树不可变发布版本的 pgvector 向量仓库。 */
@Repository
@RequiredArgsConstructor
public class IntentTreeVectorRepo {

    private static final String INSERT_SQL = """
            INSERT INTO lingxi_vector.intent_tree_vector
                (intent_tree_id, intent_tree_version_id, intent_node_id, intent_code,
                 vector_kind, source_ordinal, source_hash, embedding_model_instance_id,
                 embedding_dimension, embedding)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector)
            """;

    private final JdbcTemplate jdbcTemplate;

    /** 批量写入一个新版本的全部派生向量；发布版本只追加，不覆盖。 */
    public void insertBatch(List<IntentVectorRow> rows) {
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, 100, (statement, row) -> {
            statement.setLong(1, row.getIntentTreeId());
            statement.setLong(2, row.getIntentTreeVersionId());
            statement.setString(3, row.getIntentNodeId());
            statement.setString(4, row.getIntentCode());
            statement.setString(5, row.getVectorKind());
            statement.setInt(6, row.getSourceOrdinal());
            statement.setString(7, row.getSourceHash());
            statement.setLong(8, row.getEmbeddingModelInstanceId());
            statement.setInt(9, row.getVector().length);
            statement.setString(10, toVectorLiteral(row.getVector()));
        });
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

    /** 单条意图语义来源及其向量。 */
    @Getter
    @Builder
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class IntentVectorRow {
        private final Long intentTreeId;
        private final Long intentTreeVersionId;
        private final String intentNodeId;
        private final String intentCode;
        private final String vectorKind;
        private final Integer sourceOrdinal;
        private final String sourceHash;
        private final Long embeddingModelInstanceId;
        private final float[] vector;
    }
}
