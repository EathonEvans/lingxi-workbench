package com.lingxi.workbench.knowledge.evaluation;

import com.lingxi.workbench.knowledge.model.bo.KnowledgeSearchRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Objects;
import java.util.Set;

/**
 * 一条查询与权限断言；空期望集合表示应当没有命中。
 * @param id 数据集内唯一标识
 * @param request 真实检索输入，用户身份仍由当前 ACL 校验
 * @param expectedChunkIds 相关片段标注，用于计算召回率与首个正确结果排名
 * @param expectDenied 是否预期被权限拒绝
 */
public record KnowledgeEvaluationSample(@NotBlank @Size(max = 100) String id, @NotNull @Valid KnowledgeSearchRequest request,
        @NotNull @Size(max = 100) Set<@NotNull @Positive Long> expectedChunkIds, @NotNull Boolean expectDenied) {
    /** 拒绝样本不允许同时声明可读取的片段。 */
    @AssertTrue(message = "拒绝样本不能同时配置期望片段")
    public boolean isExpectationConsistent() {
        return !Boolean.TRUE.equals(expectDenied) || Objects.isNull(expectedChunkIds) || expectedChunkIds.isEmpty();
    }
}
