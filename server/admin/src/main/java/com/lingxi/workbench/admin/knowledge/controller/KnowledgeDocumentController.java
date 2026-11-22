package com.lingxi.workbench.admin.knowledge.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.lingxi.workbench.admin.knowledge.model.request.CreateTextDocumentRequest;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeDocumentPageQuery;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeDocumentVO;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentService;
import com.lingxi.workbench.framework.convention.ApiResponse;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.context.UserContext;
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
 * 管理端知识库文档上传与管理接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/knowledge-documents")
@RequiredArgsConstructor
public class KnowledgeDocumentController {
    private final KnowledgeDocumentService service;

    /**
     * 分页查询知识库文档及处理状态
     */
    @GetMapping("/list")
    @SaCheckPermission("knowledge:document:list")
    public ApiResponse<PageResult<KnowledgeDocumentVO>> list(@Valid KnowledgeDocumentPageQuery query) {
        return ApiResponse.success(service.page(query));
    }

    /**
     * 上传文档，仅保存原始文件和元数据。
     */
    @PostMapping("/upload")
    @SaCheckPermission("knowledge:document:manage")
    public ApiResponse<KnowledgeDocumentVO> upload(@RequestParam @NotNull(message = "知识库ID不能为空") Long knowledgeBaseId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.upload(knowledgeBaseId, file, UserContext.userId()));
    }

    /**
     * 录入文本，仅保存原始文件和元数据。
     */
    @PostMapping("/create-text")
    @SaCheckPermission("knowledge:document:manage")
    public ApiResponse<KnowledgeDocumentVO> createText(@Valid @RequestBody CreateTextDocumentRequest request) {
        return ApiResponse.success(service.createText(request, UserContext.userId()));
    }

    /**
     * 获取原始文档的临时云存储预览地址。
     */
    @GetMapping("/preview/{id}")
    @SaCheckPermission("knowledge:document:list")
    public ApiResponse<String> preview(@PathVariable Long id) {
        return ApiResponse.success(service.previewUrl(id));
    }

    /**
     * 删除非处理中的文档及其原始文件。
     */
    @PostMapping("/delete/{id}")
    @SaCheckPermission("knowledge:document:manage")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id, UserContext.userId());
        return ApiResponse.success();
    }
}
