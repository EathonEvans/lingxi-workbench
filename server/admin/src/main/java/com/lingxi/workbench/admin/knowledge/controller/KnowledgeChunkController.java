package com.lingxi.workbench.admin.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.knowledge.model.request.ExecuteKnowledgeChunkRequest;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeChunkPageQuery;
import com.lingxi.workbench.admin.knowledge.model.request.UpdateKnowledgeChunkRequest;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkResultVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeChunkVO;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeChunkService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.context.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端知识库文档处理与分块查询接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/knowledge-chunks")
@RequiredArgsConstructor
public class KnowledgeChunkController {

    private final KnowledgeChunkService chunkService;
    /**
     * 用户确认文档信息后，提交完整异步处理任务。
     */
    @PostMapping("/execute/{documentId}")
    @SaCheckPermission("knowledge:document:manage")
    public ApiResponse<KnowledgeChunkResultVO> execute(@PathVariable Long documentId, @Valid @RequestBody ExecuteKnowledgeChunkRequest request) {
        return ApiResponse.success(chunkService.execute(documentId, request, UserContext.userId()));
    }

    /**
     * 查询任务当前状态，管理端通过该接口轮询处理进度。
     */
    @GetMapping("/task/{taskId}")
    @SaCheckPermission("knowledge:list")
    public ApiResponse<KnowledgeChunkResultVO> task(@PathVariable Long taskId) {
        return ApiResponse.success(chunkService.task(taskId));
    }

    /**
     * 查询文档最近一次分块任务。
     */
    @GetMapping("/latest-task/{documentId}")
    @SaCheckPermission("knowledge:list")
    public ApiResponse<KnowledgeChunkResultVO> latestTask(@PathVariable Long documentId) {
        return ApiResponse.success(chunkService.latestTask(documentId));
    }

    /**
     * 分页查询Chunk，避免一次性返回整篇文档。
     */
    @GetMapping("/page")
    @SaCheckPermission("knowledge:list")
    public ApiResponse<PageResult<KnowledgeChunkVO>> page(@Valid KnowledgeChunkPageQuery query) {
        return ApiResponse.success(chunkService.page(query));
    }

    /**
     * 人工修订已生效的Chunk，并同步重新生成该Chunk向量。
     */
    @PostMapping("/update/{chunkId}")
    @SaCheckPermission("knowledge:document:manage")
    public ApiResponse<KnowledgeChunkVO> update(
            @PathVariable Long chunkId,
            @Valid @RequestBody UpdateKnowledgeChunkRequest request) {
        return ApiResponse.success(chunkService.update(chunkId, request, UserContext.userId()));
    }

}
