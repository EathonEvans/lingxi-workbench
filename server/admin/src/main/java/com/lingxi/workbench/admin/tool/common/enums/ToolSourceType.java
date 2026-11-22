package com.lingxi.workbench.admin.tool.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tool 定义来源。
 *
 * <p>Agent 始终绑定 Tool 主体，运行端再依据来源选择本地执行器或 MCP Server。</p>
 */
@Getter
@RequiredArgsConstructor
public enum ToolSourceType {

    /** 由应用运行端注册 Java 执行器的本地工具。 */
    LOCAL_FUNCTION("LOCAL_FUNCTION", "本地函数"),

    /** 由已启用 MCP Server 提供的远端工具。 */
    MCP("MCP", "MCP 工具");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;

    /** 管理端展示名称。 */
    private final String describe;
}
