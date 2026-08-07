package com.lingxi.workbench.workflow.model.intent;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Application 对独立意图树的路由配置。
 *
 * <p>这是 Application 发布快照（ApplicationVersion）的一部分。发布时，
 * {@code intentTreeId} 指向的意图树的当前生效版本会被解析并锁定为
 * {@code intentTreeVersionId}，之后该意图树即使发布新版本也不会影响
 * 已发布的 ApplicationVersion。</p>
 *
 * <p>Schema V1-V4 将该结构写入 {@code intentBinding} 并作为强制路由；Schema V5
 * 将同一稳定结构写入 {@code optionalFastRoutes}，只对确定命中的已映射叶子直达，
 * 其他请求进入 Planner 或默认 ReAct Agent。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code enabled} — 是否启用路由</li>
 *   <li>{@code intentTreeId} — 草稿中指向的意图树主键（逻辑外键）。发布快照中该字段保留</li>
 *   <li>{@code intentTreeVersionId} — 发布快照中锁定并解析的 IntentTreeVersion 主键。
 *       草稿中该字段为空，仅在 ApplicationVersion 中有值</li>
 *   <li>{@code fallbackPolicy} — V1-V4 强制路由使用的兜底文案；V5 为空并交由模型兜底</li>
 *   <li>{@code mappings} — 叶子意图编码到执行目标的映射列表；V5 只包含需要快速直达的叶子</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationIntentBinding {

    /** 是否启用意图路由 */
    private boolean enabled;

    /** 意图树主键（逻辑外键） */
    private Long intentTreeId;

    /** 发布时锁定的意图树版本主键，草稿中为空 */
    private Long intentTreeVersionId;

    /** V1-V4 兜底文案策略 */
    private IntentFallbackPolicy fallbackPolicy;

    /** 叶子意图到执行目标的映射列表 */
    private List<ApplicationIntentMapping> mappings;

    public List<ApplicationIntentMapping> mappings() {
        return mappings == null ? List.of() : List.copyOf(mappings);
    }
}
