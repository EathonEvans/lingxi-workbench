package com.lingxi.workbench.application.runtime.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 在指定意图树版本、Embedding 模型和向量维度范围内执行精确余弦相似度召回的只读 Repo。
 *
 * <p>直接使用 JDBC Template 而非 MyBatis-Plus Mapper，原因：</p>
 * <ul>
 *   <li>pgvector 的 {@code <=>} 余弦距离操作符需要原始 SQL 字符串拼接向量字面量</li>
 *   <li>向量字段需要 {@code ::vector} 类型强制转换</li>
 *   <li>子查询中的最大相似度聚合需要原生 SQL 表达</li>
 * </ul>
 *
 * <p>召回策略：</p>
 * <ol>
 *   <li>限定 {@code intent_tree_version_id}、{@code embedding_model_instance_id}、
 *       {@code embedding_dimension} 三个维度，确保版本隔离和模型一致性</li>
 *   <li>对每个意图节点的原型向量和所有正例向量计算余弦相似度</li>
 *   <li>按 {@code intent_node_id + intent_code} 分组取最大相似度，
 *       即一个叶子意图的最终得分 = max(原型相似度, 各正例相似度)</li>
 *   <li>按相似度降序、编码升序（确定性排序）返回前 topK 个候选</li>
 * </ol>
 *
 * <p>注意：反例不进入向量索引。阈值过滤由上层处理，
 * 本 Repo 只负责精确召回，不承担最终语义决策。</p>
 */
@Repository
@RequiredArgsConstructor
public class IntentVectorRuntimeRepo {

    /**
     * pgvector 余弦相似度召回 SQL。
     *
     * <p>{@code 1 - (embedding <=> ?::vector)} 将 pgvector 的余弦距离
     * 转换为余弦相似度（取值范围 0~1，越大越相似）。</p>
     *
     * <p>参数顺序：向量字面量、意图树版本 ID、Embedding 模型实例 ID、维度、Top-K。</p>
     */
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

    /**
     * 在指定意图树版本内按余弦相似度召回 Top-K 个叶子意图候选。
     *
     * @param queryVector               用户输入经 Embedding 模型向量化后的浮点数组
     * @param intentTreeVersionId       意图树版本主键，限定召回范围
     * @param embeddingModelInstanceId  Embedding 模型实例主键，确保维度一致
     * @param topK                      召回数量上限
     * @return 按相似度降序排列的候选列表，每个候选包含节点 ID、意图编码和最高相似度
     */
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

    /**
     * 将浮点数组转换为 pgvector 可识别的向量字面量格式。
     *
     * <p>例如 {@code [0.12, 0.98, 0.45]} → {@code "[0.12,0.98,0.45]"}。</p>
     *
     * <p>注意：浮点数直接使用 {@code Float.toString()} 拼接，未做额外格式化。
     * 高精度场景下需要关注浮点串行化的精度损失。</p>
     *
     * @param vector 浮点数组
     * @return pgvector 向量字面量字符串
     */
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

    /**
     * 候选召回结果行。
     *
     * @param intentNodeId 意图树节点唯一标识
     * @param intentCode   意图稳定编码，用于匹配 Application 映射表
     * @param similarity   该叶子所有原型和正例向量中的最高余弦相似度（0~1）
     */
    public record IntentCandidateRow(String intentNodeId, String intentCode, double similarity) {
    }
}
