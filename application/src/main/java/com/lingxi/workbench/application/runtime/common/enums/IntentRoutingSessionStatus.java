package com.lingxi.workbench.application.runtime.common.enums;

/**
 * 意图澄清会话的生命周期状态。
 *
 * <p>状态流转图：</p>
 * <pre>
 * PENDING
 * ├── 用户选择/补槽位成功 → RESOLVED / COMPLETED
 * ├── 用户取消 → CANCELLED
 * ├── 超时（10 分钟） → EXPIRED
 * └── 超轮次/异常 → FAILED
 * </pre>
 *
 * <p>PENDING 状态的会话在每条新消息到达时被检查，
 * 优先由 {@code ClariationService} 处理而非重新识别意图。</p>
 */
public enum IntentRoutingSessionStatus {

    /** 等待用户澄清/补充信息 */
    PENDING,

    /** 歧义已消解，目标意图已确定 */
    RESOLVED,

    /** 用户主动取消 */
    CANCELLED,

    /** 会话已过期 */
    EXPIRED,

    /** 所有槽位收集完成，即将执行 */
    COMPLETED,

    /** 澄清失败（超轮次或内部异常） */
    FAILED
}