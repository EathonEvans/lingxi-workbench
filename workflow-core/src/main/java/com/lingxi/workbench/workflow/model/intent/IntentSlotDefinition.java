package com.lingxi.workbench.workflow.model.intent;

import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 叶子意图需要从用户请求中提取的结构化槽位（参数）定义。
 *
 * <p>每个槽位对应一个业务参数。当叶子意图被识别后，运行时检查所有
 * {@code required=true} 的槽位是否已从用户输入中收集到。缺失的必填槽位会触发
 * MISSING_SLOT 类型的澄清会话，由用户逐项补充。</p>
 *
 * <p>槽位值在澄清收集完成后以 {@code "已确认参数：slotCode=value"} 的形式拼接到
 * 执行查询尾部，供下游 Agent 或 Workflow 消费。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code code} — 槽位稳定编码，同一叶子内唯一</li>
 *   <li>{@code name} — 槽位用户可读名称，用于澄清时间："请补充「订单号」"</li>
 *   <li>{@code description} — 槽位的详细说明，帮助用户理解需要提供什么信息</li>
 *   <li>{@code valueType} — 值类型（STRING / NUMBER / DATE / BOOLEAN），
 *       V1 不做强类型校验，仅作为提示</li>
 *   <li>{@code required} — 是否必填，必填槽位缺少时触发澄清</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentSlotDefinition {

    /** 槽位稳定编码，同一叶子内唯一 */
    private String code;

    /** 用户可读名称 */
    private String name;

    /** 槽位详细说明 */
    private String description;

    /** 值类型（STRING / NUMBER / DATE / BOOLEAN） */
    private IntentSlotValueType valueType;

    /** 是否必填 */
    private boolean required;
}