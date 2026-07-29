package com.lingxi.workbench.admin.model.service;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.admin.model.model.bo.ModelConnectionTestResult;
import com.lingxi.workbench.admin.model.common.enums.ModelType;

/** 模型实例连通性测试能力。 */
public interface ModelConnectionTester {

    /** 使用供应商、密钥和模型配置执行真实接口调用。 */
    ModelConnectionTestResult test(
            String protocol,
            String baseUrl,
            String apiKey,
            String modelCode,
            ModelType modelType,
            JSONObject extensionParameters,
            int timeoutSeconds);
}
