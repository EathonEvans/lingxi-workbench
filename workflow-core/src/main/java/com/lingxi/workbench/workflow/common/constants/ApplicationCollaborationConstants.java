package com.lingxi.workbench.workflow.common.constants;

/**
 * Application 多 Agent 协作协议的稳定边界常量。
 *
 * <p>这些值是协议层面的硬限制，与运行时可配置参数（如委派次数）不同：
 * 常量定义的是"系统能接受的最大值"，草稿中配置的具体值必须在此范围内。</p>
 *
 * <p>各常量说明：</p>
 * <ul>
 *   <li>{@code APPLICATION_SCHEMA_VERSION = 13} — 当前 ApplicationDefinition 协议版本号</li>
 *   <li>{@code MAX_RESOLVED_CAPABILITIES = 50} — 发布快照最多冻结的能力数量</li>
 *   <li>{@code MIN_DELEGATIONS = 1} / {@code MAX_DELEGATIONS = 10} —
 *       单次请求委派次数范围。10 次上限平衡了复杂任务拆解需求和 Token 开销</li>
 * </ul>
 */
public interface ApplicationCollaborationConstants {

    /**
     * ApplicationDefinition 协议版本号
     */
    int APPLICATION_SCHEMA_VERSION = 13;

    /**
     * 发布快照最多冻结的能力数量
     */
    int MAX_RESOLVED_CAPABILITIES = 50;

    /**
     * 最小委派次数
     */
    int MIN_DELEGATIONS = 1;

    /**
     * 最大委派次数
     */
    int MAX_DELEGATIONS = 10;

}
