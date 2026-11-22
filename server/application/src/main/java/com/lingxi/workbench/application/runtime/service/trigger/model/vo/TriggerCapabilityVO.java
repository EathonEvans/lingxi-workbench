package com.lingxi.workbench.application.runtime.service.trigger.model.vo;

import lombok.Getter;
import lombok.Setter;

/** 可绑定的执行能力项（来自冻结能力目录）。 */
@Getter
@Setter
public class TriggerCapabilityVO {

    /** 能力编码。 */
    private String code;

    /** 能力名称。 */
    private String name;

    /** 能力类型：AGENT / WORKFLOW。 */
    private String type;

    /** 绑定目标版本主键（字符串形式）。 */
    private String targetVersionId;
}
