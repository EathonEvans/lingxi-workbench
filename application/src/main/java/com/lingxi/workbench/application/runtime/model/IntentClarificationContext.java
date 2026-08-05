package com.lingxi.workbench.application.runtime.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 澄清会话持久化的运行时上下文，以 JSONB 格式存储在
 * {@code IntentRoutingSessionEntity.context} 字段中。
 *
 * <p>该对象记录一次澄清会话的完整状态：</p>
 * <ul>
 *   <li><b>歧义场景</b>：{@code candidateCodes} 保存所有接近的候选意图编码，
 *       {@code intentCode} 初始为空，用户选择后填充。</li>
 *   <li><b>缺槽位场景</b>：{@code intentCode} 已确定，
 *       {@code missingSlotCodes} 列出待收集的必填槽位编码队列（FIFO），
 *       {@code collectedSlots} 逐项累积已收集的槽位值。</li>
 * </ul>
 *
 * <p>{@code originalQuery} 在澄清开始时保存用户的原始请求文本，
 * 澄清完成后的执行查询中拼接原始请求和已收集参数，
 * 而非用户在澄清过程中的简短回复（如"第一个"、"A10086"）。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code originalQuery} — 澄清开始时的原始用户请求，澄清完成后作为执行查询的基础</li>
 *   <li>{@code candidateCodes} — 歧义场景中的候选意图编码列表（按相似度降序）</li>
 *   <li>{@code intentCode} — 确定的意图编码，歧义消解后或槽位收集场景中设置</li>
 *   <li>{@code missingSlotCodes} — 待收集的必填槽位编码队列（FIFO）</li>
 *   <li>{@code collectedSlots} — 已收集的槽位编码到值的映射（有序 LinkedHashMap）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentClarificationContext {

    /** 澄清开始时的原始用户请求文本 */
    private String originalQuery;

    /** 歧义场景中的候选意图编码列表 */
    private List<String> candidateCodes;

    /** 已确定的意图编码 */
    private String intentCode;

    /** 待收集的必填槽位编码队列 */
    private List<String> missingSlotCodes;

    /** 已收集的槽位编码到值的映射 */
    private Map<String, String> collectedSlots;
}