package com.lingxi.workbench.workflow.common.constants;

/**
 * Application 多 Agent 协作协议的稳定边界常量。
 *
 * <p>这些值是协议层面的硬限制，与运行时可配置参数（如委派次数）不同：
 * 常量定义的是"系统能接受的最大值"，草稿中配置的具体值必须在此范围内。</p>
 *
 * <p>各常量说明：</p>
 * <ul>
 *   <li>{@code APPLICATION_SCHEMA_VERSION = 2} —
 *       当前 ApplicationDefinition 协议版本号。V1 只有 Workflow，
 *       V2 新增 collaboration 和 intentBinding</li>
 *   <li>{@code MIN_MEMBER_AGENTS = 1} —
 *       启用协作时最少需要的成员 Agent 数量</li>
 *   <li>{@code MAX_MEMBER_AGENTS = 8} —
 *       最多允许的成员 Agent 数量。超过此值会影响 Supervisor 的 Prompt 长度
 *       和 LLM 的选择准确性</li>
 *   <li>{@code MIN_DELEGATIONS = 1} / {@code MAX_DELEGATIONS = 10} —
 *       单次请求委派次数范围。10 次上限平衡了复杂任务拆解需求和 Token 开销</li>
 *   <li>{@code SUPERVISOR_MAX_DEPTH = 1} —
 *       V1 只允许 Supervisor → 成员 Agent 的单层调用，成员不能继续委派</li>
 * </ul>
 */
public interface ApplicationCollaborationConstants {

    /** ApplicationDefinition 协议版本号 */
    int APPLICATION_SCHEMA_VERSION = 2;

    /** 最少成员 Agent 数量 */
    int MIN_MEMBER_AGENTS = 1;

    /** 最多成员 Agent 数量 */
    int MAX_MEMBER_AGENTS = 8;

    /** 最小委派次数 */
    int MIN_DELEGATIONS = 1;

    /** 最大委派次数 */
    int MAX_DELEGATIONS = 10;

    /** Supervisor 委派最大深度，V1 固定为 1 */
    int SUPERVISOR_MAX_DEPTH = 1;
}