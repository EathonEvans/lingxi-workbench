package com.lingxi.workbench.admin.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.auth.context.SysUserContext;
import com.lingxi.workbench.admin.knowledge.model.request.CreateTextDocumentRequest;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeDocumentPageQuery;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeDocumentContentVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeDocumentVO;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.convention.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端知识库文档上传与解析接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/knowledge-documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {
    private final KnowledgeDocumentService service;

    /**
     * 分页查询知识库文档及解析状态。
     */
    @GetMapping("/list")
    @SaCheckPermission("knowledge:document:list")
    public ApiResponse<PageResult<KnowledgeDocumentVO>> list(@Valid KnowledgeDocumentPageQuery query) {
        return ApiResponse.success(service.page(query));
    }

    /**
     * 上传文档并创建异步解析任务。
     */
    @PostMapping("/upload")
    @SaCheckPermission("knowledge:document:manage")
    public ApiResponse<KnowledgeDocumentVO> upload(@RequestParam @NotNull(message = "知识库ID不能为空") Long knowledgeBaseId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.upload(knowledgeBaseId, file, operatorId()));
    }

    /**
     * 录入文本并创建异步解析任务。
     */
    @PostMapping("/create-text")
    @SaCheckPermission("knowledge:document:manage")
    public ApiResponse<KnowledgeDocumentVO> createText(@Valid @RequestBody CreateTextDocumentRequest request) {
        return ApiResponse.success(service.createText(request, operatorId()));
    }

    /**
     * 查询解析后的纯文本正文。
     */
    @GetMapping("/content/{id}")
    @SaCheckPermission("knowledge:document:list")
    public ApiResponse<KnowledgeDocumentContentVO> content(@PathVariable Long id) {
        return ApiResponse.success(service.content(id));
    }

    /**
     * 重试失败的文档解析任务。
     */
    @PostMapping("/retry/{id}")
    @SaCheckPermission("knowledge:document:manage")
    public ApiResponse<Void> retry(@PathVariable Long id) {
        service.retry(id, operatorId());
        return ApiResponse.success();
    }

    /**
     * 删除非解析中的文档及其原始文件。
     */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("knowledge:document:manage")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id, operatorId());
        return ApiResponse.success();
    }

    private Long operatorId() {
        return SysUserContext.get().id();
    }
}
