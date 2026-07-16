package com.lingxi.workbench.admin.model.service;

import com.lingxi.workbench.admin.model.model.bo.ModelConnectionTestResult;

/** 模型实例连通性测试能力。 */
public interface ModelConnectionTester {

    /** 使用供应商、密钥和模型配置执行真实接口调用。 */
    ModelConnectionTestResult test(
            String protocol,
            String baseUrl,
            String apiKey,
            String modelCode,
            int timeoutSeconds);
}
