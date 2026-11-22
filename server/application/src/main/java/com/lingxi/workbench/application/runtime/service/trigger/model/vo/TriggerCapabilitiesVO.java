package com.lingxi.workbench.application.runtime.service.trigger.model.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 可绑定能力列表及其所属应用版本，前端创建触发器时回传版本绑定。 */
@Getter
@Setter
public class TriggerCapabilitiesVO {

    /** 应用主键（字符串形式）。 */
    private String applicationId;

    /** 当前发布的应用版本主键（字符串形式），创建触发器时冻结绑定。 */
    private String applicationVersionId;

    /** 可绑定的 AGENT/WORKFLOW 能力列表。 */
    private List<TriggerCapabilityVO> capabilities;
}
