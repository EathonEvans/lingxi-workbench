package com.lingxi.workbench.workflow.model.intent;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Application 对独立意图树的绑定配置。
 *
 * <p>这是 Application 发布快照（ApplicationVersion）的一部分。发布时，
 * {@code intentTreeId} 指向的意图树的当前生效版本会被解析并锁定为
 * {@code intentTreeVersionId}，之后该意图树即使发布新版本也不会影响
 * 已发布的 ApplicationVersion。</p>
 *
 * <p>如果 {@code enabled=false}，运行时跳过意图路由，请求直接进入主 Workflow。
 * 这是向后兼容的关键开关——未配置意图树的已有 Application 不受影响。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code enabled} — 是否启用意图路由。未启用时请求直接进入主 Workflow</li>
 *   <li>{@code intentTreeId} — 草稿中指向的意图树主键（逻辑外键）。发布快照中该字段保留</li>
 *   <li>{@code intentTreeVersionId} — 发布快照中锁定并解析的 IntentTreeVersion 主键。
 *       草稿中该字段为空，仅在 ApplicationVersion 中有值</li>
 *   <li>{@code fallbackPolicy} — 用户可见的兜底文案，包含未识别意图和澄清失败的回复文本</li>
 *   <li>{@code mappings} — 叶子意图编码到执行目标的映射列表。发布时校验每个启用叶子
 *       都必须有有效映射</li>
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

    /** 兜底文案策略 */
    private IntentFallbackPolicy fallbackPolicy;

    /** 叶子意图到执行目标的映射列表 */
    private List<ApplicationIntentMapping> mappings;

    public List<ApplicationIntentMapping> mappings() {
        return mappings == null ? List.of() : List.copyOf(mappings);
    }
}