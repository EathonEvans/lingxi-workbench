package com.lingxi.workbench.admin.knowledge.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 人工修订Chunk正文请求。 */
public record UpdateKnowledgeChunkRequest(
        @NotBlank(message = "Chunk正文不能为空")
        @Size(max = 30000, message = "Chunk正文不能超过30000个字符")
        String content) {
}
