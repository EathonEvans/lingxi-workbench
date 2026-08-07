package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
@Getter
@RequiredArgsConstructor
public enum IntentRoutingSessionStatus {

    /** 等待用户澄清/补充信息 */
    PENDING("PENDING", "等待澄清"),

    /** 歧义已消解，目标意图已确定 */
    RESOLVED("RESOLVED", "意图已确认"),

    /** 用户主动取消 */
    CANCELLED("CANCELLED", "用户已取消"),

    /** 会话已过期 */
    EXPIRED("EXPIRED", "会话已过期"),

    /** 所有槽位收集完成，即将执行 */
    COMPLETED("COMPLETED", "槽位已收集"),

    /** 澄清失败（超轮次或内部异常） */
    FAILED("FAILED", "澄清失败");

    @EnumValue
    private final String code;
    private final String describe;
}
