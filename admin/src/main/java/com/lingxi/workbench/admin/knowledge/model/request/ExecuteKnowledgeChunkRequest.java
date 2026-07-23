package com.lingxi.workbench.admin.knowledge.model.request;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeChunkStrategy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 手动执行文档分块请求。
 *
 * @param strategy     分块策略
 * @param chunkSize    单个Chunk的目标Token数
 * @param chunkOverlap 相邻Chunk的重叠Token数
 */
public record ExecuteKnowledgeChunkRequest(
        @NotNull(message = "请选择分块策略")
        KnowledgeChunkStrategy strategy,

        @NotNull(message = "请输入Chunk大小")
        @Min(value = 50, message = "Chunk大小不能小于50 Token")
        @Max(value = 4000, message = "Chunk大小不能超过4000 Token")
        Integer chunkSize,

        @NotNull(message = "请输入Chunk重叠大小")
        @Min(value = 0, message = "Chunk重叠大小不能小于0")
        @Max(value = 1000, message = "Chunk重叠大小不能超过1000 Token")
        Integer chunkOverlap) {
}
