package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeVariableDefinition;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.prompt.common.constants.PromptVariableConstants;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import com.lingxi.workbench.infra.ai.variable.RuntimeVariableCatalog;
import com.lingxi.workbench.infra.ai.variable.common.enums.BuiltInRuntimeVariable;
import com.lingxi.workbench.infra.ai.variable.common.enums.RuntimeVariableChannel;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 严格渲染 Agent 使用的角色 Prompt 模板。
 *
 * <p>运行端只接受平台已实现取值来源的内置变量，并校验消息角色、发布声明、
 * 模板引用、变量类型和角色安全边界。任何不一致都说明发布快照不可执行，
 * 必须明确失败。</p>
 */
@Component
public class PromptTemplateRenderer {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile(PromptVariableConstants.VARIABLE_PLACEHOLDER_REGEXP);

    /**
     * 渲染已发布 System Prompt。
     *
     * @param snapshot  PromptVersion 运行快照
     * @param variables 本轮对话唯一的运行时变量容器
     * @return 完成变量替换的 System Prompt
     */
    public String renderSystemPrompt(PromptRuntimeSnapshot snapshot, RuntimeVariables variables) {
        return renderPrompt(
                snapshot,
                variables,
                PromptRole.SYSTEM,
                RuntimeVariableChannel.SYSTEM_PROMPT);
    }

    /**
     * 渲染已发布 User Prompt，用户问题和知识上下文只能在该低优先级消息中使用。
     */
    public String renderUserPrompt(PromptRuntimeSnapshot snapshot, RuntimeVariables variables) {
        return renderPrompt(
                snapshot,
                variables,
                PromptRole.USER,
                RuntimeVariableChannel.USER_PROMPT);
    }

    /**
     * 渲染 QueryRewrite 节点使用的任务 Prompt。
     *
     * <p>该 Prompt 在模型协议中仍是 SYSTEM 角色，但变量权限由独立的问题改写
     * 通道控制，不能因此放宽普通回答 System Prompt 的安全边界。</p>
     */
    public String renderQueryRewritePrompt(
            PromptRuntimeSnapshot snapshot,
            RuntimeVariables variables) {
        return renderPrompt(
                snapshot,
                variables,
                PromptRole.SYSTEM,
                RuntimeVariableChannel.QUERY_REWRITE_PROMPT);
    }

    private String renderPrompt(
            PromptRuntimeSnapshot snapshot,
            RuntimeVariables variables,
            PromptRole expectedRole,
            RuntimeVariableChannel variableChannel) {
        if (snapshot.role() != expectedRole) {
            throw invalidSnapshot(snapshot.promptVersionId(), "Prompt角色必须为" + expectedRole.getCode());
        }
        Map<String, PromptRuntimeVariableDefinition> definitions = indexDefinitions(snapshot);
        Matcher matcher = VARIABLE_PATTERN.matcher(snapshot.content());
        Set<String> referencedNames = new LinkedHashSet<>();
        StringBuilder rendered = new StringBuilder(snapshot.content().length());
        while (matcher.find()) {
            String variableName = matcher.group(1);

            referencedNames.add(variableName);

            String replacement = resolveReplacement(
                    snapshot.promptVersionId(),
                    variableName,
                    definitions.get(variableName),
                    variables,
                    variableChannel);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);

        validateTemplateSyntax(snapshot.promptVersionId(), rendered);
        validateUnusedDefinitions(snapshot.promptVersionId(), definitions.keySet(), referencedNames);
        return rendered.toString();
    }

    private Map<String, PromptRuntimeVariableDefinition> indexDefinitions(PromptRuntimeSnapshot snapshot) {
        Map<String, PromptRuntimeVariableDefinition> definitions = new LinkedHashMap<>();
        for (PromptRuntimeVariableDefinition definition : snapshot.variables()) {
            if (definition.valid()) {
                throw invalidSnapshot(snapshot.promptVersionId(), "变量声明缺少名称或类型");
            }
            PromptRuntimeVariableDefinition previous = definitions.putIfAbsent(definition.name(), definition);
            if (previous != null) {
                throw invalidSnapshot(snapshot.promptVersionId(), "变量重复声明：" + definition.name());
            }
        }
        return definitions;
    }

    private String resolveReplacement(
            Long promptVersionId,
            String variableName,
            PromptRuntimeVariableDefinition definition,
            RuntimeVariables variables,
            RuntimeVariableChannel variableChannel) {
        if (definition == null) {
            throw invalidSnapshot(promptVersionId, "模板使用了未声明变量：" + variableName);
        }
        BuiltInRuntimeVariable variable = RuntimeVariableCatalog.find(variableName)
                .orElseThrow(() -> invalidSnapshot(
                        promptVersionId,
                        "运行端不支持变量：" + variableName));
        if (!variable.supportsChannel(variableChannel)) {
            throw invalidSnapshot(
                    promptVersionId,
                    variableChannel.getDescribe() + "禁止使用变量：" + variableName);
        }
        if (!variable.getType().getCode().equals(definition.type())) {
            throw invalidSnapshot(promptVersionId, "变量类型与内置契约不一致：" + variableName);
        }

        Optional<String> renderedValue = variables.render(variable);
        if (definition.required() && renderedValue.filter(value -> !value.isBlank()).isEmpty()) {
            throw invalidSnapshot(promptVersionId, "必填变量没有运行时值：" + variableName);
        }
        return renderedValue.orElse("");
    }

    private static void validateTemplateSyntax(Long promptVersionId, StringBuilder rendered) {
        if (rendered.indexOf("{{") >= 0 || rendered.indexOf("}}") >= 0) {
            throw invalidSnapshot(promptVersionId, "模板中存在无法识别的变量占位符");
        }
    }

    private static void validateUnusedDefinitions(Long promptVersionId, Set<String> declaredNames, Set<String> referencedNames) {
        Set<String> unusedNames = new LinkedHashSet<>(declaredNames);
        unusedNames.removeAll(referencedNames);
        if (!unusedNames.isEmpty()) {
            throw invalidSnapshot(promptVersionId, "存在未使用的变量声明：" + String.join("、", unusedNames));
        }
    }

    private static BusinessException invalidSnapshot(Long promptVersionId, String reason) {
        return new BusinessException(HttpStatusEnum.CONFLICT, "PromptVersion运行配置无效，versionId=" + promptVersionId + "，" + reason);
    }
}
