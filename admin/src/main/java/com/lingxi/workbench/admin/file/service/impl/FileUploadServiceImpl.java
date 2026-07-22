package com.lingxi.workbench.admin.file.service.impl;

import com.lingxi.workbench.admin.config.FileUploadProperties;
import com.lingxi.workbench.admin.file.common.enums.FileBasePath;
import com.lingxi.workbench.admin.file.model.vo.FileUploadVO;
import com.lingxi.workbench.admin.file.service.FileUploadService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * 文件上传业务实现，负责路径解析、对象存储写入和签名URL生成。
 */
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {

    private static final Tika TIKA = new Tika();
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    private final ObjectStorage objectStorage;
    private final FileUploadProperties fileProperties;

    @Override
    public FileUploadVO upload(MultipartFile file, FileBasePath basePath, Long userId) {
        validateFile(file);
        try {
            byte[] content = file.getBytes();
            String contentType = TIKA.detect(content, file.getOriginalFilename());
            String extension = resolveExtension(basePath, contentType, file.getOriginalFilename());
            String objectKey = basePath.resolvePath(userId, extension);

            objectStorage.put(objectKey, content, contentType);
            Duration expires = Duration.ofHours(fileProperties.getUrlExpireHours());
            String url = objectStorage.presignedGetUrl(objectKey, expires);

            return new FileUploadVO(objectKey, url, file.getOriginalFilename(), file.getSize());
        } catch (IOException exception) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "读取上传文件失败");
        }
    }

    /** 校验文件非空且未超过统一大小限制。 */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "请选择需要上传的文件");
        }
        if (file.getSize() > fileProperties.getMaxSize()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "上传文件大小超过限制");
        }
    }

    /** 头像和Logo只接受经过内容识别的安全图片格式。 */
    private static String resolveExtension(
            FileBasePath basePath, String contentType, String originalFileName) {
        if (basePath == FileBasePath.AVATAR || basePath == FileBasePath.LOGO) {
            String extension = IMAGE_EXTENSIONS.get(contentType);
            if (extension == null) {
                throw new BusinessException(
                        HttpStatusEnum.BAD_REQUEST, "仅支持JPG、PNG、WebP和GIF图片");
            }
            return extension;
        }
        return extractExtension(originalFileName);
    }

    /** 提取并清洗普通文件扩展名，避免路径注入和异常长后缀。 */
    private static String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "bin";
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,10}") ? extension : "bin";
    }
}
