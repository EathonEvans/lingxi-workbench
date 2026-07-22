package com.lingxi.workbench.admin.file.common.enums;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.annotation.EnumValue;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传基础路径枚举，每个枚举值定义存储路径模板。
 *
 * <p>路径占位符说明：
 * <ul>
 *   <li>{@code {userId}} — 当前操作用户主键</li>
 *   <li>{@code {uuid}} — 随机UUID</li>
 *   <li>{@code {yyyyMM}} — 当前年月</li>
 *   <li>{@code {ext}} — 文件原始后缀</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum FileBasePath {

    AVATAR("avatar", "用户头像", "avatars/{userId}/{uuid}.{ext}"),
    LOGO("logo", "业务Logo", "logos/{uuid}.{ext}"),
    COMMON("common", "通用文件", "public/{yyyyMM}/{uuid}.{ext}");

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    @EnumValue
    private final String code;
    private final String describe;
    private final String pathPattern;

    /** 根据接口路径编码解析文件分类。 */
    public static FileBasePath fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> CharSequenceUtil.equals(value.code, code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.BAD_REQUEST, "不支持该文件上传类型"));
    }

    /**
     * 将路径模板中的占位符替换为实际值。
     *
     * @param userId  当前用户主键
     * @param ext     文件后缀
     * @return 实际存储路径
     */
    public String resolvePath(Long userId, String ext) {
        String safeExt = CharSequenceUtil.isBlank(ext) ? "bin" : ext.toLowerCase(Locale.ROOT);
        return pathPattern
                .replace("{userId}", userId.toString())
                .replace("{uuid}", UUID.randomUUID().toString())
                .replace("{yyyyMM}", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")))
                .replace("{ext}", safeExt);
    }

    /**
     * 校验对象是否属于指定用户的头像路径，防止用户绑定其他账号或其他业务的文件。
     */
    public boolean ownsAvatar(String objectKey, Long userId) {
        if (this != AVATAR || CharSequenceUtil.isBlank(objectKey) || userId == null) {
            return false;
        }
        String prefix = "avatars/" + userId + "/";
        if (!objectKey.startsWith(prefix)) {
            return false;
        }
        String fileName = objectKey.substring(prefix.length());
        int extensionSeparator = fileName.lastIndexOf('.');
        if (extensionSeparator <= 0 || extensionSeparator == fileName.length() - 1) {
            return false;
        }
        String uuid = fileName.substring(0, extensionSeparator);
        if (!isUuid(uuid)) {
            return false;
        }
        String extension = fileName.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.contains(extension);
    }

    /** UUID文件名由服务端生成，避免固定对象Key覆盖后命中浏览器旧缓存。 */
    private static boolean isUuid(String value) {
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
