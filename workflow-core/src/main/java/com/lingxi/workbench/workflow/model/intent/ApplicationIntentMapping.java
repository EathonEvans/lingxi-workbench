package com.lingxi.workbench.workflow.model.intent;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Application 对一个叶子意图的执行映射。
 *
 * <p>每条映射将意图树中的一个叶子编码（{@code intentCode}）绑定到三类执行目标之一：</p>
 * <ul>
 *   <li><b>WORKFLOW</b> — 交给当前 ApplicationVersion 内的主 Workflow 执行。
 *       此时不需要 {@code targetId}，因为目标就是该 Application 自己的 Workflow。</li>
 *   <li><b>AGENT</b> — 交给指定的 Agent 执行，包括普通 RAG_CHAT 和 SUPERVISOR 类型。
 *       草稿中 {@code targetId} 保存 Agent 稳定主键，发布时解析并锁定为
 *       {@code targetVersionId}。</li>
 *   <li><b>FIXED_RESPONSE</b> — 直接返回预定义的固定文本，不调用任何模型。
 *       适用于闲聊兜底、能力说明等不需要 AI 处理的场景。</li>
 * </ul>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code intentCode} — 意图树叶子节点的稳定编码</li>
 *   <li>{@code targetType} — 执行目标类型：WORKFLOW / AGENT / FIXED_RESPONSE</li>
 *   <li>{@code targetId} — 当 targetType=AGENT 时的 Agent 稳定主键；
 *       当 targetType=WORKFLOW 和 FIXED_RESPONSE 时为空</li>
 *   <li>{@code targetVersionId} — 发布快照中锁定并解析的 AgentVersion 主键。
 *       草稿中可以留空（表示发布时跟随当前生效版本），发布后由控制面填充</li>
 *   <li>{@code fixedResponse} — 当 targetType=FIXED_RESPONSE 时直接返回的文本内容</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationIntentMapping {

    /** 意图树叶子节点的稳定编码 */
    private String intentCode;

    /** 执行目标类型 */
    private IntentExecutionTargetType targetType;

    /** Agent 稳定主键（仅 AGENT 类型使用） */
    private Long targetId;

    /** 发布快照中锁定的 AgentVersion 主键 */
    private Long targetVersionId;

    /** 固定答复文本（仅 FIXED_RESPONSE 类型使用） */
    private String fixedResponse;
}