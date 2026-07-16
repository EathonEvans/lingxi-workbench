package com.lingxi.workbench.model.model.bo;

import java.math.BigDecimal;
import java.util.List;

/** application 内部使用的模型运行配置业务对象。 */
public record ModelRuntimeConfigBO(
        Long instanceId,
        String protocol,
        String baseUrl,
        String secretCiphertext,
        String secretIv,
        String modelCode,
        BigDecimal temperature,
        BigDecimal topP,
        Integer maxTokens,
        List<String> stopSequences,
        Integer timeoutSeconds,
        Integer maxRetries) {

    /** 禁止日志输出密文和 IV。 */
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
