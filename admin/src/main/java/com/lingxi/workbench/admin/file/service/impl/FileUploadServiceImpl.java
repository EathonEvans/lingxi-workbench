package com.lingxi.workbench.admin.file.service.impl;

import com.lingxi.workbench.admin.config.FileUploadProperties;
import com.lingxi.workbench.admin.file.common.enums.FileBasePath;
import com.lingxi.workbench.admin.file.model.vo.FileUploadVO;
import com.lingxi.workbench.admin.file.service.FileUploadService;
import com.lingxi.workbench.framework.storage.impl.AliOssObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

/**
 * 文件上传业务实现，负责路径解析、对象存储写入和签名URL生成。
 */
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {

    private final AliOssObjectStorage objectStorage;
    private final FileUploadProperties fileProperties;

    @Override
    public FileUploadVO upload(MultipartFile file, FileBasePath basePath, Long userId) throws IOException {
        String ext = extractExtension(file.getOriginalFilename());
        String objectKey = basePath.resolvePath(userId, ext);
        byte[] content = file.getBytes();

        objectStorage.put(objectKey, content, file.getContentType());
        Duration expires = Duration.ofHours(fileProperties.getUrlExpireHours());
        String url = objectStorage.presignedGetUrl(objectKey, expires);

        return new FileUploadVO(objectKey, url, file.getOriginalFilename(), file.getSize());
    }

    private static String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "bin";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
