package com.lingxi.workbench.workflow.model.intent;

import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 意图树中的单个节点定义。
 *
 * <p>节点分为两类：</p>
 * <ul>
 *   <li><b>ROUTER</b> — 分类节点，仅用于组织意图层级，不直接映射执行目标。
 *       只能作为中间节点，其子节点可以是 ROUTER 或 LEAF。</li>
 *   <li><b>LEAF</b> — 叶子意图节点，是实际可以路由到的用户意图。
 *       其 {@code code} 字段被 Application 的映射表引用以确定执行目标。
 *       叶子节点维护正反例样本用于向量召回训练，以及槽位定义用于运行时参数收集。</li>
 * </ul>
 *
 * <p>该对象中的执行目标不绑定具体 Agent 或 Workflow，映射关系由
 * {@link ApplicationIntentMapping} 独立维护。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code id} — 节点唯一标识，在 IntentTree 内唯一，用于构建父子关系</li>
 *   <li>{@code code} — 节点稳定编码，在 IntentTree 内唯一，LEAF 节点的编码被映射表引用</li>
 *   <li>{@code parentId} — 父节点 ID，根节点为 null</li>
 *   <li>{@code name} — 用户可读的节点名称，前端展示用</li>
 *   <li>{@code description} — 叶子意图的职责描述，Supervisor 据此判断委派时机；
 *       同时作为原型向量化的语义文本来源</li>
 *   <li>{@code nodeType} — 节点类型：ROUTER 不参与路由，LEAF 是路由候选</li>
 *   <li>{@code positiveExamples} — 正例列表，每条生成一个独立向量参与相似度计算</li>
 *   <li>{@code negativeExamples} — 反例列表，不进入向量索引，仅用于离线混淆评测和 LLM 候选判定</li>
 *   <li>{@code slots} — 叶子意图需要从用户请求中提取的结构化槽位定义</li>
 *   <li>{@code clarificationQuestion} — 槽位缺失时向用户提问的内容模板，为空时使用默认提问</li>
 *   <li>{@code priority} — 同层节点的排序优先级，数值越小越靠前发布时按 priority + name 排序</li>
 *   <li>{@code enabled} — 是否启用，禁用的节点不参与向量化也不出现在候选结果中</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IntentNodeDefinition {

    /** 节点唯一标识，在 IntentTree 内唯一 */
    private String id;

    /** 节点稳定编码，在 IntentTree 内唯一；LEAF 节点的编码被 Application 映射表引用 */
    private String code;

    /** 父节点 ID，根节点为 null */
    private String parentId;

    /** 用户可读名称 */
    private String name;

    /** 叶子意图的职责描述，也是向量化语义文本的主要来源 */
    private String description;

    /** 节点类型：ROUTER 或 LEAF */
    private IntentNodeType nodeType;

    /** 正例列表，每条独立生成向量参与候选召回 */
    private List<String> positiveExamples;

    /** 反例列表，不进入向量索引 */
    private List<String> negativeExamples;

    /** 叶子意图的槽位定义 */
    private List<IntentSlotDefinition> slots;

    /** 槽位缺失时的澄清提问模板 */
    private String clarificationQuestion;

    /** 排序优先级，数值越小越靠前 */
    private Integer priority;

    /** 是否启用 */
    private boolean enabled;
}