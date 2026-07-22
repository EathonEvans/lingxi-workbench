package com.lingxi.workbench.admin.file.model.vo;

/**
 * 文件上传响应。
 *
 * @param objectKey         对象存储中的文件标识
 * @param url               对象外网访问地址（带签名临时URL）
 * @param originalFileName  原始文件名
 * @param size              文件字节数
 */
public record FileUploadVO(
        String objectKey,
        String url,
        String originalFileName,
        long size) {
}
