package com.lingxi.workbench.application.runtime.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Application 运行端只读实体。
 */
@Data
@TableName("lingxi_core.ai_application")
public class RuntimeApplicationEntity {
    private Long id;
    private String name;
    private String code;
    private String description;
    private String status;
    private Long publishedVersionId;
}
