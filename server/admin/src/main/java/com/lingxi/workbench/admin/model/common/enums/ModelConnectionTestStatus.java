package com.lingxi.workbench.admin.model.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 模型实例连通性测试状态。 */
@Getter
@RequiredArgsConstructor
public enum ModelConnectionTestStatus {
    /** 尚未执行连通性测试。 */
    UNTESTED("UNTESTED", "未测试"),
    /** 最近一次连通性测试成功。 */
    SUCCESS("SUCCESS", "测试成功"),
    /** 最近一次连通性测试失败。 */
    FAILED("FAILED", "测试失败");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
