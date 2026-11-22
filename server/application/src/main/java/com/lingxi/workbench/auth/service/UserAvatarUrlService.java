package com.lingxi.workbench.auth.service;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.framework.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 应用端用户头像访问地址服务，将持久化对象Key转换为浏览器可访问的临时地址。
 */
@Service
public class UserAvatarUrlService {

    private final ObjectStorage objectStorage;
    private final Duration urlExpiry;

    public UserAvatarUrlService(
            ObjectStorage objectStorage,
            @Value("${lingxi.file.url-expire-hours:1}") int urlExpireHours) {
        this.objectStorage = objectStorage;
        this.urlExpiry = Duration.ofHours(urlExpireHours);
    }

    /**
     * 解析头像值；历史外链保持不变，对象Key生成临时签名地址。
     *
     * @param storedValue 数据库中保存的头像值
     * @return 浏览器可访问的头像地址，未设置头像时返回 {@code null}
     */
    public String resolve(String storedValue) {
        if (CharSequenceUtil.isBlank(storedValue)) {
            return null;
        }
        if (storedValue.startsWith("http://") || storedValue.startsWith("https://")) {
            return storedValue;
        }
        return objectStorage.presignedGetUrl(storedValue, urlExpiry);
    }
}
