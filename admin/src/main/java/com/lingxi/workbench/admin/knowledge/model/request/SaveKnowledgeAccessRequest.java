package com.lingxi.workbench.admin.knowledge.model.request;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeAccessMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

/** 原子替换知识库读取范围；空授权的受限知识库拒绝全部应用端读取。 */
@Data
public class SaveKnowledgeAccessRequest {
    @NotNull
    private KnowledgeAccessMode mode;
    @NotNull @Size(max = 100)
    private Set<@NotNull @Positive Long> userIds;
    @NotNull @Size(max = 100)
    private Set<@NotNull @Positive Long> roleIds;
    @NotNull @Size(max = 100)
    private Set<@NotNull @Positive Long> departmentIds;
}
