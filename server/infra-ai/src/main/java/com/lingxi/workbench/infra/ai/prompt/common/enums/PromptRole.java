package com.lingxi.workbench.infra.ai.prompt.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Prompt 消息角色。
 *
 * <p>管理端使用角色约束可选版本，运行端使用同一契约校验 PromptVersion
 * 最终进入 SystemMessage 还是 UserMessage。</p>
 */
@Getter
@RequiredArgsConstructor
public enum PromptRole {

    SYSTEM("SYSTEM", "系统"),
    USER("USER", "用户");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;

    /** 中文说明，仅用于管理端展示。 */
    private final String describe;
}
