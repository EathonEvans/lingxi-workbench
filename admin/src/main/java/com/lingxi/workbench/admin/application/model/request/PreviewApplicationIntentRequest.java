package com.lingxi.workbench.admin.application.model.request;

import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 对当前浏览器中的 Application 草稿执行一次只读意图测试。 */
@Data
public class PreviewApplicationIntentRequest {

    @NotBlank(message = "测试问题不能为空")
    @Size(max = 4000, message = "测试问题不能超过4000个字符")
    private String query;
    @Valid
    private AgentCollaborationDefinition collaboration;
    @Valid
    private ApplicationIntentBinding intentRouting;
    @Valid
    private MemoryConfig memory;
    @Valid
    private ApplicationContextPolicy contextPolicy;
    @Valid
    private CapabilityCatalogSnapshot capabilityCatalog;
    @Valid
    private ApplicationRuntimePolicy runtimePolicy;
}
