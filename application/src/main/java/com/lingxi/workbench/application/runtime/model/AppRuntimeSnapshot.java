package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
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
        Objects.requireNonNull(definition, "Application运行定义不能为空");
        if (applicationCode == null || applicationCode.isBlank()) {
            throw new IllegalArgumentException("applicationCode不能为空");
        }
        if (definition.schemaVersion() != APPLICATION_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Application运行定义版本不受支持");
        }
        boolean hasDefaultAgent = Objects.nonNull(definition.defaultAgent())
                && Objects.nonNull(definition.defaultAgent().agentId())
                && Objects.nonNull(definition.defaultAgent().agentVersionId());
        if (!hasDefaultAgent) {
            throw new IllegalArgumentException("Application缺少默认ReAct Agent");
        }
        boolean catalogContainsDefault = definition.capabilityCatalog().capabilities().stream()
                .anyMatch(capability -> "default_agent".equals(capability.getCode())
                        && capability.getType() == ApplicationCapabilityType.REACT_AGENT
                        && Objects.equals(capability.getTargetId(), definition.defaultAgent().agentId())
                        && Objects.equals(capability.getTargetVersionId(), definition.defaultAgent().agentVersionId()));
        if (!catalogContainsDefault) {
            throw new IllegalArgumentException("Application能力目录缺少锁定的默认Agent");
        }
        if (Objects.nonNull(definition.optionalFastRoutes())) {
            validateFastRoutes(definition);
        }
    }

    private static void validateFastRoutes(ApplicationDefinition definition) {
        if (!definition.optionalFastRoutes().isEnabled()) {
            return;
        }
        if (Objects.isNull(definition.optionalFastRoutes().getIntentTreeId())
                || Objects.isNull(definition.optionalFastRoutes().getIntentTreeVersionId())
                || definition.optionalFastRoutes().mappings().isEmpty()) {
            throw new IllegalArgumentException("Application可选快速路由快照不完整");
        }
        for (ApplicationIntentMapping mapping : definition.optionalFastRoutes().mappings()) {
            validateFastRouteMapping(definition, mapping);
        }
    }

    private static void validateFastRouteMapping(ApplicationDefinition definition, ApplicationIntentMapping mapping) {
        if (mapping == null || mapping.getIntentCode() == null || mapping.getIntentCode().isBlank()
                || mapping.getTargetType() == null) {
            throw new IllegalArgumentException("Application可选快速路由映射不完整");
        }
        if (mapping.getTargetType() == IntentExecutionTargetType.FIXED_RESPONSE) {
            if (mapping.getFixedResponse() == null || mapping.getFixedResponse().isBlank()) {
                throw new IllegalArgumentException("Application可选快速路由固定答复不能为空");
            }
            return;
        }
        if (mapping.getCapabilityCode() == null || mapping.getCapabilityCode().isBlank()) {
            throw new IllegalArgumentException("Application快速路由能力编码不能为空");
        }
        ApplicationCapabilityType expectedType = mapping.getTargetType() == IntentExecutionTargetType.WORKFLOW
                ? ApplicationCapabilityType.WORKFLOW
                : ApplicationCapabilityType.REACT_AGENT;
        ApplicationCapabilityDefinition capability = definition.capabilityCatalog().capabilities().stream()
                .filter(item -> Objects.equals(item.getCode(), mapping.getCapabilityCode()))
                .filter(item -> item.getType() == expectedType)
                .filter(item -> Objects.equals(item.getTargetId(), mapping.getTargetId()))
                .filter(item -> Objects.equals(item.getTargetVersionId(), mapping.getTargetVersionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Application快速路由未引用锁定能力目录"));
    }
}
