package com.lingxi.workbench.admin.knowledge.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 通过文本录入创建知识库文档。
 *
 * @param knowledgeBaseId 知识库ID
 * @param name            文档名称
 * @param content         文档内容
 */
public record CreateTextDocumentRequest(
        @NotNull(message = "知识库ID不能为空") Long knowledgeBaseId,
        @NotBlank(message = "文档名称不能为空") @Size(max = 128, message = "文档名称不能超过128个字符") String name,
        @NotBlank(message = "文档内容不能为空") @Size(max = 2000000, message = "文档内容不能超过200万字符") String content) {}
