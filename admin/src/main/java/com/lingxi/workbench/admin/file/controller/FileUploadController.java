package com.lingxi.workbench.admin.file.controller;

import com.lingxi.workbench.admin.file.common.enums.FileBasePath;
import com.lingxi.workbench.admin.file.model.vo.FileUploadVO;
import com.lingxi.workbench.admin.file.service.FileUploadService;
import com.lingxi.workbench.framework.context.UserContext;
import com.lingxi.workbench.framework.convention.ApiResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端文件上传公共接口。
 *
 * <p>通过 {@code {basePath}} 路径变量区分存储类别，后端自行解析存储路径。</p>
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    /**
     * 文件上传，根据 basePath 枚举值写入对应存储路径。
     *
     * @param basePath 存储类别枚举
     * @param file     上传文件
     * @return 上传结果（含签名URL）
     */
    @PostMapping(value = "/upload/{basePath}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileUploadVO> upload(
            @PathVariable String basePath,
            @RequestPart("file") @NotNull MultipartFile file) {
        return ApiResponse.success(fileUploadService.upload(
                file, FileBasePath.fromCode(basePath), UserContext.userId()));
    }
}
