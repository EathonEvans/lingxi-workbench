package com.lingxi.workbench.workflow.model.intent;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Application 对一个叶子意图的执行映射。
 *
 * <p>草稿中的 targetVersionId 允许为空；Application 发布时必须解析并锁定。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApplicationIntentMapping {

    private String intentCode;
    private IntentExecutionTargetType targetType;
    private Long targetId;
    private Long targetVersionId;
    private String fixedResponse;
}
