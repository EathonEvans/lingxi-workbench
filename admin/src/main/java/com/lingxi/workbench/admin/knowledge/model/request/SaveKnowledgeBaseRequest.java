package com.lingxi.workbench.admin.knowledge.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 新增或修改知识库请求。 */
public record SaveKnowledgeBaseRequest(
        @NotBlank(message = "知识库名称不能为空") @Size(max = 64, message = "知识库名称不能超过64个字符") String name,
        @NotBlank(message = "知识库编码不能为空")
        @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$", message = "知识库编码格式不正确") String code,
        @Size(max = 500, message = "知识库描述不能超过500个字符") String description,
        @NotNull(message = "请选择Embedding模型") Long embeddingModelInstanceId,
        @NotNull(message = "知识库状态不能为空") EnableStatus status) {}
