package com.lingxi.workbench.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "lingxi.file")
public class FileUploadProperties {


    private long maxUploadSize;

    private int urlExpireHours;

}
