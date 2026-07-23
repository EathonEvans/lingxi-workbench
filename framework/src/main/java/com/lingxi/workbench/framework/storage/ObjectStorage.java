package com.lingxi.workbench.framework.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * 统一对象存储抽象，封装不同云厂商和自建对象存储的上传、下载、删除及临时访问能力。
 *
 * <p>当前支持 MinIO（开发环境）和阿里云 OSS（生产环境），通过配置 {@code lingxi.storage.type} 切换。</p>
 */
public interface ObjectStorage {

    /**
     * 上传对象至存储桶。
     *
     * @param objectKey   对象唯一标识
     * @param content     对象字节内容
     * @param contentType MIME 类型
     */
    void put(String objectKey, byte[] content, String contentType);

    /**
     * 从存储桶读取对象内容，调用方负责关闭流。
     *
     * @param objectKey 对象唯一标识
     * @return 对象输入流
     */
    InputStream get(String objectKey);

    /**
     * 从存储桶删除对象。
     *
     * @param objectKey 对象唯一标识
     */
    void delete(String objectKey);

    /**
     * 生成带签名的临时访问URL，仅有效期内容可通过HTTP GET直接访问私有对象。
     *
     * @param objectKey 对象唯一标识
     * @param expires   有效期
     * @return 带签名的临时访问URL
     */
    String presignedGetUrl(String objectKey, Duration expires);

    /**
     * 生成可覆盖响应Content-Type的临时访问URL。
     *
     * <p>适用于文本预览等需要明确字符集的场景。对象历史元数据不正确时，
     * 也可以通过响应头覆盖避免浏览器错误推断编码。</p>
     *
     * @param objectKey          对象唯一标识
     * @param expires            有效期
     * @param responseContentType 下载响应使用的Content-Type
     * @return 带签名的临时访问URL
     */
    default String presignedGetUrl(String objectKey, Duration expires, String responseContentType) {
        return presignedGetUrl(objectKey, expires);
    }
}
