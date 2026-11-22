package com.lingxi.workbench.knowledge.model.bo;

import com.lingxi.workbench.infra.ai.rerank.model.RerankPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;

import java.util.Objects;

/** 服务端组装的检索输入；userId 来自 Tool 执行身份，其余资源与策略来自 AgentVersion。 */
@Getter
@Builder(toBuilder = true)
public class KnowledgeSearchRequest {
    private final String executionId;
    @NotBlank @Size(max = 10000)
    private final String query;
    @NotNull @Positive
    private final Long userId;
    @NotNull @Positive
    private final Long knowledgeBaseId;
    @NotNull @Positive
    private final Long indexVersionId;
    @NotNull @Positive
    private final Long embeddingModelInstanceId;
    @Min(1) @Max(100)
    private final int topK;
    @DecimalMin("0.0") @DecimalMax("1.0")
    private final double similarityThreshold;
    @Min(1) @Max(600)
    private final int timeoutSeconds;
    @Valid
    private final RerankPolicy rerank;
    /** 离线文件也是外部输入，候选数量约束与 Admin 发布规则保持一致。 */
    @AssertTrue(message = "重排序候选数不能小于最终返回数")
    public boolean isCandidateLimitValid() {
        return Objects.isNull(rerank) || Objects.isNull(rerank.candidateTopK()) || rerank.candidateTopK() >= topK;
    }
}
