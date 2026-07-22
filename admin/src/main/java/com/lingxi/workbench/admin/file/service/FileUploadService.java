package com.lingxi.workbench.admin.file.service;

import com.lingxi.workbench.admin.file.common.enums.FileBasePath;
import com.lingxi.workbench.admin.file.model.vo.FileUploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端文件上传业务接口。
 */
public interface FileUploadService {

    /**
     * 上传文件至对象存储，返回包含签名临时URL的上传结果。
     *
     * @param file     上传文件
     * @param basePath 存储基本路径类别
     * @param userId   操作用户主键
     * @return 上传结果（含 objectKey、签名URL、原始文件名、大小）
     */
    FileUploadVO upload(MultipartFile file, FileBasePath basePath, Long userId);
}
