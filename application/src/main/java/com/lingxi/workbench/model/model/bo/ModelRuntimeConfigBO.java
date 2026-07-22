package com.lingxi.workbench.model.model.bo;

import java.math.BigDecimal;
import java.util.List;

/** application 内部使用的模型运行配置业务对象。 */
public record ModelRuntimeConfigBO(
        /** 模型实例主键。 */
        Long instanceId,
        /** 协议类型。 */
        String protocol,
        /** 接口基础地址。 */
        String baseUrl,
        /** AES-256-GCM 加密后的密钥密文。 */
        String secretCiphertext,
        /** 密钥初始化向量。 */
        String secretIv,
        /** 供应商模型标识。 */
        String modelCode,
        /** 温度参数。 */
        BigDecimal temperature,
        /** 核采样参数。 */
        BigDecimal topP,
        /** 单次生成最大 Token 数。 */
        Integer maxTokens,
        /** 停止生成序列列表。 */
        List<String> stopSequences,
        /** 请求超时时间（秒）。 */
        Integer timeoutSeconds,
        /** 最大重试次数。 */
        Integer maxRetries) {

    /**
     * 覆盖默认 toString，将密钥密文和 IV 替换为 ****** 掩码，
     * 防止加密凭证通过日志或异常堆栈外泄。
     */
    @Override
    public String toString() {
        return "ModelRuntimeConfigBO[instanceId=" + instanceId
                + ", protocol=" + protocol
                + ", baseUrl=" + baseUrl
                + ", secretCiphertext=******"
                + ", secretIv=******"
                + ", modelCode=" + modelCode
                + ", temperature=" + temperature
                + ", topP=" + topP
                + ", maxTokens=" + maxTokens
                + ", stopSequences=" + stopSequences
                + ", timeoutSeconds=" + timeoutSeconds
                + ", maxRetries=" + maxRetries + "]";
    }
}
