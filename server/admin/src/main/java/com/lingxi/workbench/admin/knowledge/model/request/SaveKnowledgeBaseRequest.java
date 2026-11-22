package com.lingxi.workbench.admin.knowledge.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新增或修改知识库请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveKnowledgeBaseRequest {
    /** 知识库名称 */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 64, message = "知识库名称不能超过64个字符")
    private String name;
    /** 知识库编码 */
    @NotBlank(message = "知识库编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_-]{1,63}$", message = "知识库编码格式不正确")
    private String code;
    /** 知识库描述 */
    @Size(max = 500, message = "知识库描述不能超过500个字符")
    private String description;
    /** Embedding模型实例ID */
    @NotNull(message = "请选择Embedding模型")
    private Long embeddingModelInstanceId;
    /** 知识库状态 */
    @NotNull(message = "知识库状态不能为空")
    private EnableStatus status;
}
