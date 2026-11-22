package com.lingxi.workbench.admin.file.service;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.admin.config.FileUploadProperties;
import com.lingxi.workbench.framework.storage.ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 头像访问地址服务，统一处理对象Key、临时签名地址和历史外链。
 */
@Service
@RequiredArgsConstructor
public class AvatarUrlService {

    private final ObjectStorage objectStorage;
    private final FileUploadProperties fileProperties;

    /**
     * 将数据库中的头像值转换为前端可访问地址。
     *
     * <p>新数据存储对象Key；历史HTTP地址保持原样，避免将完整URL再次作为对象Key签名。</p>
     */
    public AvatarUrl resolve(String storedValue) {
        if (CharSequenceUtil.isBlank(storedValue)) {
            return new AvatarUrl(null, null);
        }
        if (storedValue.startsWith("http://") || storedValue.startsWith("https://")) {
            return new AvatarUrl(storedValue, null);
        }
        Duration expires = Duration.ofHours(fileProperties.getUrlExpireHours());
        return new AvatarUrl(objectStorage.presignedGetUrl(storedValue, expires), storedValue);
    }

    /** 头像可访问地址及其持久化对象Key。
     *
     * @param url       前端可访问的地址（签名临时URL或历史外链地址）
     * @param objectKey 对象存储中的持久化标识
     */
    public record AvatarUrl(String url, String objectKey) {
    }
}
