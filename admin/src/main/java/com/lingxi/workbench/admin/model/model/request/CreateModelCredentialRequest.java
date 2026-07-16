package com.lingxi.workbench.admin.model.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 新增模型密钥请求。 */
public record CreateModelCredentialRequest(
        @NotNull(message = "请选择供应商") @Positive(message = "供应商主键不合法") Long providerId,
        @NotBlank(message = "请输入密钥名称") @Size(max = 64, message = "密钥名称不能超过 64 个字符")
                String name,
        @NotBlank(message = "请输入 API Key") @Size(max = 2048, message = "API Key 长度不能超过 2048 个字符")
                String apiKey) {}
