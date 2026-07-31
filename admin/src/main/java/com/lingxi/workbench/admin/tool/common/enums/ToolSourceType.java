package com.lingxi.workbench.admin.tool.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tool 定义来源。
 *
 * <p>第一阶段只开放本地 Function Calling。后续接入 MCP 时扩展该枚举和对应运行时适配器，
 * 不需要改变 Tool 主体模型。</p>
 */
@Getter
@RequiredArgsConstructor
public enum ToolSourceType {

    /** 由应用运行端注册 Java 执行器的本地工具。 */
    LOCAL_FUNCTION("LOCAL_FUNCTION", "本地函数");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;

    /** 管理端展示名称。 */
    private final String describe;
}
