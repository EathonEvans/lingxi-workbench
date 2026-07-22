package com.lingxi.workbench.admin.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.knowledge.model.request.ExecuteKnowledgeChunkRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkResultVO;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeChunkService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.context.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端知识库文档分块接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/knowledge-chunks")
@RequiredArgsConstructor
public class KnowledgeChunkController {

    private final KnowledgeChunkService chunkService;

    /**
     * 用户确认解析结果后，手动执行文档分块并返回预览结果。
     */
    @PostMapping("/execute/{documentId}")
    @SaCheckPermission("knowledge:document:manage")
    public ApiResponse<KnowledgeChunkResultVO> execute(@PathVariable Long documentId, @Valid @RequestBody ExecuteKnowledgeChunkRequest request) {
        return ApiResponse.success(chunkService.execute(documentId, request, UserContext.userId()));
    }
}
