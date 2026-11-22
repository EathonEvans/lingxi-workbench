package com.lingxi.workbench.admin.model.model.bo;

import com.lingxi.workbench.admin.model.common.enums.ModelConnectionTestStatus;

/**
 * 模型实例连通性测试结果，仅在服务端内部流转。
 *
 * @param status    测试状态
 * @param latencyMs 延迟毫秒
 * @param errorCode 错误码
 * @param message   结果消息
 */
public record ModelConnectionTestResult(

        ModelConnectionTestStatus status,

        int latencyMs,

        String errorCode,

        String message) {

    /** 创建测试成功结果。 */
    public static ModelConnectionTestResult success(int latencyMs) {
        return new ModelConnectionTestResult(
                ModelConnectionTestStatus.SUCCESS, latencyMs, null, "连接成功");
    }

    /** 创建测试失败结果。 */
    public static ModelConnectionTestResult failed(
            int latencyMs, String errorCode, String message) {
        return new ModelConnectionTestResult(
                ModelConnectionTestStatus.FAILED, latencyMs, errorCode, message);
    }
}
