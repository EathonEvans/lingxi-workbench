package com.lingxi.workbench.admin.application.service;

import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;

import java.util.Optional;

/** Application 发布校验读取意图树生效版本的稳定边界。 */
public interface ApplicationIntentTreeReferenceGateway {

    /** 按意图树主键读取启用状态和当前不可变发布定义。 */
    Optional<IntentTreeReference> findPublishedTree(Long intentTreeId);

    /**
     * @param intentTreeId 意图树稳定主键
     * @param active 意图树是否启用
     * @param versionId 当前生效版本主键
     * @param definition 当前生效版本定义
     */
    record IntentTreeReference(
            Long intentTreeId,
            boolean active,
            Long versionId,
            IntentTreeDefinition definition) {
    }
}
