package com.lingxi.workbench.admin.tool.model.vo;

import lombok.Builder;
import lombok.Data;

/** Agent 配置页使用的启用 Tool 轻量选项。 */
@Data
@Builder
public class ToolOptionVO {

    private String code;

    private String name;

    private String description;
}
