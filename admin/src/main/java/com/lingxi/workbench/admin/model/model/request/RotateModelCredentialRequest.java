package com.lingxi.workbench.admin.model.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 轮换模型密钥请求。 */
public record RotateModelCredentialRequest(
        @NotBlank(message = "请输入 API Key") @Size(max = 2048, message = "API Key 长度不能超过 2048 个字符")
                String apiKey) {}
