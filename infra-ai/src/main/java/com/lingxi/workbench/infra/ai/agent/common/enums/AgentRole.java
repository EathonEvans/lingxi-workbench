package com.lingxi.workbench.infra.ai.agent.common.enums;

import com.alibaba.fastjson2.annotation.JSONCreator;
import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Agent 角色决定协作职责，不表达 ReAct 或 Plan & Execute 等执行方式。
 */
@Getter
@RequiredArgsConstructor
public enum AgentRole {

    SPECIALIST("SPECIALIST", "领域专家"),
    SUPERVISOR("SUPERVISOR", "主Agent");

    /**
     * 稳定编码，同时作为数据库和发布快照存储值。
     */
    @EnumValue
    @JSONField(value = true)
    private final String code;

    /**
     * 中文说明，仅用于管理端展示。
     */
    private final String describe;

    /**
     * 根据发布快照中的稳定编码解析 Agent 角色，未知编码必须阻断运行。
     */
    @JSONCreator
    public static AgentRole fromCode(String code) {
        for (AgentRole role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("不支持的Agent角色：" + code);
    }
}
