package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;

import java.util.Objects;

import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;

/**
 * 当前 Application 生效版本的不可变运行快照。
 *
 * @param applicationId        应用主键
 * @param applicationCode      应用稳定编码
 * @param versionId 当前生效的版本主键
 * @param definition           版本中冻结的执行入口、能力、路由、协作与记忆定义
 */
public record AppRuntimeSnapshot(
        Long applicationId,
        String applicationCode,
        Long versionId,
        ApplicationDefinition definition) {

    /**
     * 快照一旦创建就必须是完整、可执行的已发布 Application。
     *
     * <p>这里校验的是运行模型自身的不变量，不能只依赖 Controller 参数校验：
     * 定时任务、消息消费或内部调用未来也可能创建运行快照。</p>
     */
    public AppRuntimeSnapshot {
        Objects.requireNonNull(applicationId, "applicationId不能为空");
        Objects.requireNonNull(versionId, "applicationVersionId不能为空");
        if (Objects.isNull(definition)) {
            throw new IllegalArgumentException("Application运行定义不能为空");
        }
        if (applicationCode == null || applicationCode.isBlank()) {
            throw new IllegalArgumentException("applicationCode不能为空");
        }
        if (definition.schemaVersion() != APPLICATION_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Application运行定义版本不受支持");
        }
        FallbackAgent fallback = definition.fallback();
        if (fallback == null || fallback.cap() == null || fallback.cap().isBlank()) {
            throw new IllegalArgumentException("Application缺少fallback Agent能力编码");
        }
        if (!definition.hasCompletePublishedDefinition()) {
            throw new IllegalArgumentException("Application发布快照缺少必填配置");
        }
        long fallbackCount = definition.capabilityCatalog().capabilities().stream()
                .filter(capability -> fallback.cap().equals(capability.getCode()))
                .filter(capability -> capability.getType() == ApplicationCapabilityType.AGENT)
                .filter(capability -> Objects.nonNull(capability.getTargetId()))
                .filter(capability -> Objects.nonNull(capability.getTargetVersionId()))
                .count();
        if (fallbackCount != 1) {
            throw new IllegalArgumentException("Application能力目录缺少唯一锁定的fallback Agent");
        }
        if (Objects.nonNull(definition.intentRouting())) {
            validateIntentRouting(definition);
        }
    }

    private static void validateIntentRouting(ApplicationDefinition definition) {
        if (!definition.intentRouting().isEnabled()) {
            return;
        }
        if (Objects.isNull(definition.intentRouting().getIntentTreeId())
                || Objects.isNull(definition.intentRouting().getIntentTreeVersionId())
                || definition.intentRouting().mappings().isEmpty()) {
            throw new IllegalArgumentException("Application意图路由快照不完整");
        }
        for (ApplicationIntentMapping mapping : definition.intentRouting().mappings()) {
            validateIntentRoutingMapping(definition, mapping);
        }
    }

    private static void validateIntentRoutingMapping(ApplicationDefinition definition, ApplicationIntentMapping mapping) {
        if (mapping == null || mapping.getIntentCode() == null || mapping.getIntentCode().isBlank()
                || mapping.getTargetType() == null) {
            throw new IllegalArgumentException("Application意图路由映射不完整");
        }
        if (mapping.getTargetType() == IntentExecutionTargetType.FIXED_RESPONSE) {
            if (mapping.getFixedResponse() == null || mapping.getFixedResponse().isBlank()) {
                throw new IllegalArgumentException("Application意图路由固定答复不能为空");
            }
            return;
        }
        if (mapping.getCapabilityCode() == null || mapping.getCapabilityCode().isBlank()) {
            throw new IllegalArgumentException("Application意图路由能力编码不能为空");
        }
        ApplicationCapabilityType expectedType = mapping.getTargetType() == IntentExecutionTargetType.WORKFLOW
                ? ApplicationCapabilityType.WORKFLOW
                : ApplicationCapabilityType.AGENT;
        ApplicationCapabilityDefinition capability = definition.capabilityCatalog().capabilities().stream()
                .filter(item -> Objects.equals(item.getCode(), mapping.getCapabilityCode()))
                .filter(item -> item.getType() == expectedType)
                .filter(item -> Objects.equals(item.getTargetId(), mapping.getTargetId()))
                .filter(item -> Objects.equals(item.getTargetVersionId(), mapping.getTargetVersionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Application意图路由未引用锁定能力目录"));
    }
}
