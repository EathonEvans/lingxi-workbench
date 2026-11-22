package com.lingxi.workbench.infra.ai.outbound;

/**
 * 模型出站地址安全校验失败异常。
 *
 * <p>承载面向配置者的错误提示，由管理端转换为业务错误返回。</p>
 */
public class ModelOutboundSecurityException extends RuntimeException {

    public ModelOutboundSecurityException(String message) {
        super(message);
    }
}
