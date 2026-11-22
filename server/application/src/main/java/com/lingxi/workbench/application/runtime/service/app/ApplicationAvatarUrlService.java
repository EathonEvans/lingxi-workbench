package com.lingxi.workbench.application.runtime.service.app;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.framework.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** 将 ApplicationVersion 中冻结的头像对象 Key 解析为短期访问地址。 */
@Service
public class ApplicationAvatarUrlService {

    private final ObjectStorage objectStorage;
    private final Duration urlExpiry;

    public ApplicationAvatarUrlService(
            ObjectStorage objectStorage,
            @Value("${lingxi.file.url-expire-hours:1}") int urlExpireHours) {
        this.objectStorage = objectStorage;
        this.urlExpiry = Duration.ofHours(urlExpireHours);
    }

    /**
 * Application 头像只接受受控存储标识；空值由前端降级为展示名称缩写。
 *
 * @param avatar 发布版本中的头像
 * @return 浏览器可访问的签名地址，未配置头像时返回 {@code null}
 */
    public String resolve(String avatar) {
        if (CharSequenceUtil.isBlank(avatar)) {
            return null;
        }
        return objectStorage.presignedGetUrl(avatar, urlExpiry);
    }
}
