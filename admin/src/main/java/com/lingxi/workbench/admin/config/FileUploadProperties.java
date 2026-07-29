package com.lingxi.workbench.admin.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 文件上传统一配置，绑定 {@code lingxi.file} 前缀的配置项。
 *
 * <p>控制单文件大小上限和私有对象临时访问地址的有效时长。</p>
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "lingxi.file")
public class FileUploadProperties {


    /** 单个通用文件允许上传的最大字节数。 */
    @Min(value = 1, message = "文件上传大小限制必须大于0")
    private long maxSize;

    /** 私有对象临时访问地址有效时长。 */
    @Min(value = 1, message = "文件临时访问地址有效时长必须大于0")
    private int urlExpireHours;
}
