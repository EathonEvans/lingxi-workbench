package com.lingxi.workbench.infra.ai.agent.common.enums;

import com.alibaba.fastjson2.annotation.JSONCreator;
import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Agent 类型决定运行时执行职责。 */
@Getter
@RequiredArgsConstructor
public enum AgentType {

    REACT("REACT", "ReAct Agent"),
    SUPERVISOR("SUPERVISOR", "多Agent监督者");

    /** 稳定编码，同时作为数据库和发布快照存储值。 */
    @EnumValue
    @JSONField(value = true)
    private final String code;

    /** 中文说明，仅用于管理端展示。 */
    private final String describe;

    /** 根据发布快照中的稳定编码解析 Agent 类型，未知编码必须阻断运行。 */
    @JSONCreator
    public static AgentType fromCode(String code) {
        for (AgentType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的Agent类型：" + code);
    }
}
