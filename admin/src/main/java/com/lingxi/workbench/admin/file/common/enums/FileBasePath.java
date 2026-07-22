package com.lingxi.workbench.admin.file.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    AVATAR("avatar", "avatars/{userId}.{ext}"),
    LOGO("logo", "logos/{uuid}.{ext}"),
    COMMON("common", "public/{yyyyMM}/{uuid}.{ext}");

    @EnumValue
    private final String code;
    private final String pathPattern;

    /**
     * 将路径模板中的占位符替换为实际值。
     *
     * @param userId  当前用户主键
     * @param ext     文件后缀
     * @return 实际存储路径
     */
    public String resolvePath(Long userId, String ext) {
        String safeExt = ext != null ? ext.toLowerCase() : "bin";
        return pathPattern
                .replace("{userId}", userId.toString())
                .replace("{uuid}", UUID.randomUUID().toString())
                .replace("{yyyyMM}", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")))
                .replace("{ext}", safeExt);
    }
}
