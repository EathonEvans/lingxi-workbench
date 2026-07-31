package com.lingxi.workbench.admin.agent.service;

import com.lingxi.workbench.admin.agent.model.config.AgentMemoryPolicy;
import com.lingxi.workbench.admin.agent.model.config.AgentRuntimePolicy;
import com.lingxi.workbench.admin.agent.model.config.AgentToolPolicy;
import com.lingxi.workbench.admin.agent.model.config.GenerationPolicy;
import com.lingxi.workbench.admin.agent.model.config.QueryRewritePolicy;
import com.lingxi.workbench.admin.agent.model.config.RetrievalPolicy;
import com.lingxi.workbench.admin.agent.model.entity.AgentDraftEntity;
import com.lingxi.workbench.admin.agent.model.entity.AgentDraftKnowledgeEntity;
import com.lingxi.workbench.admin.agent.model.entity.AgentEntity;
import com.lingxi.workbench.admin.agent.model.vo.AgentValidationIssueVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentValidationResultVO;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.admin.model.common.enums.ModelCapability;
import com.lingxi.workbench.admin.model.common.enums.ModelConnectionTestStatus;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.model.entity.ModelInstanceEntity;
import com.lingxi.workbench.admin.model.repo.ModelInstanceRepo;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVersionStatus;
import com.lingxi.workbench.admin.prompt.model.entity.PromptEntity;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVariableDefinition;
import com.lingxi.workbench.admin.prompt.model.entity.PromptVersionEntity;
import com.lingxi.workbench.admin.prompt.repo.PromptRepo;
import com.lingxi.workbench.admin.prompt.repo.PromptVersionRepo;
import com.lingxi.workbench.admin.tool.service.ToolService;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import com.lingxi.workbench.infra.ai.variable.RuntimeVariableCatalog;
import com.lingxi.workbench.infra.ai.variable.common.enums.BuiltInRuntimeVariable;
import com.lingxi.workbench.infra.ai.variable.common.enums.RuntimeVariableChannel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Agent 草稿发布前的纯数据库与配置契约校验器。 */
@Component
@RequiredArgsConstructor
public class AgentDraftValidator {
    private final ModelInstanceRepo modelRepo;
    private final PromptRepo promptRepo;
    private final PromptVersionRepo promptVersionRepo;
    private final KnowledgeBaseRepo knowledgeBaseRepo;
    private final Validator beanValidator;
    private final ToolService toolService;

    /** 对 Agent 及其草稿执行发布前完整校验：状态、模型、Prompt、知识库、策略跨字段限制。 */
    public AgentValidationResultVO validate(
            AgentEntity agent,
            AgentDraftEntity draft,
            AgentDraftKnowledgeEntity knowledgeBinding) {
        List<AgentValidationIssueVO> issues = new ArrayList<>();
        if (agent.getStatus() != EnableStatus.ACTIVE) {
            add(issues, "AGENT_DISABLED", "停用的Agent不能发布");
        }
        validatePolicies(draft, issues);

        ModelInstanceEntity chatModel = validateModel(
                draft.getChatModelInstanceId(), ModelType.CHAT, "CHAT_MODEL", issues)
                .orElse(null);
        if (Objects.nonNull(chatModel)
                && (chatModel.getCapabilities() == null
                || !chatModel.getCapabilities().contains(ModelCapability.STREAMING))) {
            add(issues, "CHAT_MODEL_STREAMING_REQUIRED", "回答模型必须支持流式输出");
        }
        validateTools(draft, chatModel, issues);
        validatePrompt(
                draft.getSystemPromptVersionId(),
                PromptRole.SYSTEM,
                RuntimeVariableChannel.SYSTEM_PROMPT,
                "SYSTEM_PROMPT",
                issues);
        validatePrompt(
                draft.getUserPromptVersionId(),
                PromptRole.USER,
                RuntimeVariableChannel.USER_PROMPT,
                "USER_PROMPT",
                issues);

        QueryRewritePolicy rewritePolicy = draft.getRewritePolicy();
        ModelInstanceEntity rewriteModel = null;
        if (Objects.nonNull(rewritePolicy) && Boolean.TRUE.equals(rewritePolicy.getEnabled())) {
            rewriteModel = validateModel(
                    draft.getRewriteModelInstanceId(),
                    ModelType.CHAT,
                    "REWRITE_MODEL",
                    issues).orElse(null);
            validatePrompt(
                    draft.getRewritePromptVersionId(),
                    PromptRole.SYSTEM,
                    RuntimeVariableChannel.QUERY_REWRITE_PROMPT,
                    "REWRITE_PROMPT",
                    issues);
        }
        validateKnowledge(knowledgeBinding, issues);
        validateCrossLimits(draft, chatModel, rewriteModel, issues);
        return new AgentValidationResultVO(issues.isEmpty(), issues);
    }

    private void validatePolicies(
            AgentDraftEntity draft, List<AgentValidationIssueVO> issues) {
        validatePolicy(draft.getMemoryPolicy(), "MEMORY_POLICY", issues);
        validatePolicy(draft.getRewritePolicy(), "REWRITE_POLICY", issues);
        validatePolicy(draft.getRetrievalPolicy(), "RETRIEVAL_POLICY", issues);
        validatePolicy(draft.getGenerationPolicy(), "GENERATION_POLICY", issues);
        validatePolicy(draft.getRuntimePolicy(), "RUNTIME_POLICY", issues);
        validatePolicy(draft.getToolPolicy(), "TOOL_POLICY", issues);
    }

    /** 校验 Tool 已在控制面启用，并要求回答模型声明 TOOL_CALLING 能力。 */
    private void validateTools(AgentDraftEntity draft, ModelInstanceEntity chatModel, List<AgentValidationIssueVO> issues) {
        List<String> toolCodes = draft.getToolCodes();
        if (toolCodes == null) {
            add(issues, "TOOL_CODES_MISSING", "工具编码列表不能为空");
            return;
        }
        long distinctCount = toolCodes.stream().distinct().count();
        if (distinctCount != toolCodes.size()) {
            add(issues, "TOOL_CODE_DUPLICATED", "工具编码不能重复");
        }
        Set<String> activeCodes = toolService.findActiveCodes(toolCodes);
        for (String toolCode : toolCodes) {
            if (!activeCodes.contains(toolCode)) {
                add(issues, "TOOL_UNAVAILABLE", "Tool 不存在或已停用：" + toolCode);
            }
        }
        if (!toolCodes.isEmpty()
                && Objects.nonNull(chatModel)
                && (chatModel.getCapabilities() == null
                || !chatModel.getCapabilities().contains(ModelCapability.TOOL_CALLING))) {
            add(issues, "CHAT_MODEL_TOOL_CALLING_REQUIRED", "绑定工具后，回答模型必须支持工具调用");
        }
    }

    private void validatePolicy(
            Object policy, String code, List<AgentValidationIssueVO> issues) {
        if (Objects.isNull(policy)) {
            add(issues, code + "_MISSING", "缺少" + code + "配置");
            return;
        }
        for (ConstraintViolation<Object> violation : beanValidator.validate(policy)) {
            add(issues, code + "_INVALID", violation.getMessage());
        }
    }

    private Optional<ModelInstanceEntity> validateModel(
            Long modelId,
            ModelType expectedType,
            String codePrefix,
            List<AgentValidationIssueVO> issues) {
        if (Objects.isNull(modelId)) {
            add(issues, codePrefix + "_MISSING", "缺少" + expectedType.getDescribe());
            return Optional.empty();
        }
        Optional<ModelInstanceEntity> optional = modelRepo.findById(modelId);
        if (optional.isEmpty()) {
            add(issues, codePrefix + "_NOT_FOUND", "模型实例不存在");
            return Optional.empty();
        }
        ModelInstanceEntity model = optional.get();
        if (model.getModelType() != expectedType) {
            add(issues, codePrefix + "_TYPE_MISMATCH", "模型实例类型必须为" + expectedType.getCode());
        }
        if (model.getStatus() != EnableStatus.ACTIVE) {
            add(issues, codePrefix + "_DISABLED", "模型实例已停用");
        }
        if (model.getLastTestStatus() != ModelConnectionTestStatus.SUCCESS) {
            add(issues, codePrefix + "_UNAVAILABLE", "模型实例最近一次连通测试未成功");
        }
        return Optional.of(model);
    }

    private void validatePrompt(
            Long versionId,
            PromptRole expectedRole,
            RuntimeVariableChannel variableChannel,
            String codePrefix,
            List<AgentValidationIssueVO> issues) {
        if (Objects.isNull(versionId)) {
            add(issues, codePrefix + "_MISSING", "缺少已发布Prompt版本");
            return;
        }
        Optional<PromptVersionEntity> optional = promptVersionRepo.findById(versionId);
        if (optional.isEmpty()) {
            add(issues, codePrefix + "_NOT_FOUND", "Prompt版本不存在");
            return;
        }
        PromptVersionEntity version = optional.get();
        if (version.getStatus() != PromptVersionStatus.PUBLISHED) {
            add(issues, codePrefix + "_NOT_PUBLISHED", "必须引用已发布Prompt版本");
        }
        Optional<PromptEntity> promptOptional = promptRepo.findById(version.getPromptId());
        if (promptOptional.isEmpty()) {
            add(issues, codePrefix + "_PARENT_NOT_FOUND", "Prompt主记录不存在");
            return;
        }
        PromptEntity prompt = promptOptional.get();
        if (prompt.getStatus() != EnableStatus.ACTIVE) {
            add(issues, codePrefix + "_DISABLED", "Prompt已停用");
        }
        if (prompt.getRole() != expectedRole) {
            add(
                    issues,
                    codePrefix + "_ROLE_INVALID",
                    "Agent必须引用" + expectedRole.getCode() + "角色Prompt");
        }
        validatePromptVariables(version, variableChannel, codePrefix, issues);
        if (expectedRole == PromptRole.USER) {
            validateRequiredUserVariables(version, codePrefix, issues);
        }
    }

    /**
     * 变量必须具备运行时来源，并且只能进入当前 Agent 能力对应的消费通道。
     *
     * <p>PromptRole 只描述模型消息角色，不能表达同为 SYSTEM 角色的普通回答指令
     * 和问题改写任务之间的变量差异，因此此处必须按 variableChannel 校验。</p>
     */
    private void validatePromptVariables(
            PromptVersionEntity version,
            RuntimeVariableChannel variableChannel,
            String codePrefix,
            List<AgentValidationIssueVO> issues) {
        List<PromptVariableDefinition> variables = version.getVariables();
        if (variables == null) {
            return;
        }
        for (PromptVariableDefinition definition : variables) {
            Optional<BuiltInRuntimeVariable> variableOptional =
                    RuntimeVariableCatalog.find(definition.getName());
            if (variableOptional.isEmpty()) {
                add(
                        issues,
                        codePrefix + "_VARIABLE_UNSUPPORTED",
                        "Prompt包含运行端不支持的变量：" + definition.getName());
                continue;
            }
            BuiltInRuntimeVariable variable = variableOptional.get();
            if (!variable.supportsChannel(variableChannel)) {
                add(
                        issues,
                        codePrefix + "_VARIABLE_UNSAFE",
                        variableChannel.getDescribe()
                                + "禁止使用变量："
                                + definition.getName());
            }
            if (definition.getType() == null
                    || !variable.getType().getCode().equals(
                            definition.getType().getCode())) {
                add(
                        issues,
                        codePrefix + "_VARIABLE_TYPE_MISMATCH",
                        "变量"
                                + definition.getName()
                                + "的类型必须为"
                                + variable.getType().getCode());
            }
        }
    }

    /**
     * RAG_CHAT 的 User Prompt 必须消费用户问题和知识上下文，否则模型问答配置不完整。
     */
    private static void validateRequiredUserVariables(
            PromptVersionEntity version,
            String codePrefix,
            List<AgentValidationIssueVO> issues) {
        List<PromptVariableDefinition> variables =
                Objects.requireNonNullElse(version.getVariables(), List.of());
        List<String> variableNames = variables.stream()
                .map(PromptVariableDefinition::getName)
                .toList();
        requireUserVariable(
                variableNames,
                BuiltInRuntimeVariable.USER_QUERY,
                codePrefix,
                issues);
        requireUserVariable(
                variableNames,
                BuiltInRuntimeVariable.KNOWLEDGE_CONTEXT,
                codePrefix,
                issues);
    }

    private static void requireUserVariable(
            List<String> variableNames,
            BuiltInRuntimeVariable requiredVariable,
            String codePrefix,
            List<AgentValidationIssueVO> issues) {
        if (!variableNames.contains(requiredVariable.getCode())) {
            add(
                    issues,
                    codePrefix + "_VARIABLE_MISSING",
                    "USER Prompt必须声明变量：" + requiredVariable.getCode());
        }
    }

    private void validateKnowledge(
            AgentDraftKnowledgeEntity binding, List<AgentValidationIssueVO> issues) {
        if (Objects.isNull(binding)) {
            add(issues, "KNOWLEDGE_MISSING", "缺少知识库绑定");
            return;
        }
        Optional<KnowledgeBaseEntity> optional =
                knowledgeBaseRepo.findById(binding.getKnowledgeBaseId());
        if (optional.isEmpty()) {
            add(issues, "KNOWLEDGE_NOT_FOUND", "知识库不存在");
            return;
        }
        KnowledgeBaseEntity knowledgeBase = optional.get();
        if (knowledgeBase.getStatus() != EnableStatus.ACTIVE) {
            add(issues, "KNOWLEDGE_DISABLED", "知识库已停用");
        }
        if (!Objects.equals(
                knowledgeBase.getEmbeddingModelInstanceId(),
                binding.getEmbeddingModelInstanceId())) {
            add(issues, "KNOWLEDGE_EMBEDDING_CHANGED", "知识库Embedding模型已变化，请重新保存草稿");
        }
        validateModel(
                binding.getEmbeddingModelInstanceId(),
                ModelType.EMBEDDING,
                "EMBEDDING_MODEL",
                issues);
    }

    private void validateCrossLimits(
            AgentDraftEntity draft,
            ModelInstanceEntity chatModel,
            ModelInstanceEntity rewriteModel,
            List<AgentValidationIssueVO> issues) {
        AgentMemoryPolicy memory = draft.getMemoryPolicy();
        QueryRewritePolicy rewrite = draft.getRewritePolicy();
        RetrievalPolicy retrieval = draft.getRetrievalPolicy();
        GenerationPolicy generation = draft.getGenerationPolicy();
        AgentRuntimePolicy runtime = draft.getRuntimePolicy();
        AgentToolPolicy toolPolicy = draft.getToolPolicy();
        if (Objects.isNull(memory)
                || Objects.isNull(rewrite)
                || Objects.isNull(retrieval)
                || Objects.isNull(generation)
                || Objects.isNull(runtime)
                || Objects.isNull(memory.getEnabled())
                || Objects.isNull(memory.getMaxTokens())
                || Objects.isNull(rewrite.getEnabled())
                || Objects.isNull(rewrite.getMaxHistoryTokens())
                || Objects.isNull(rewrite.getMaxOutputTokens())
                || Objects.isNull(rewrite.getTimeoutMillis())
                || Objects.isNull(retrieval.getMaxContextTokens())
                || Objects.isNull(generation.getMaxOutputTokens())
                || Objects.isNull(generation.getTimeoutSeconds())
                || Objects.isNull(runtime.getMaxOutputTokens())
                || Objects.isNull(runtime.getTotalTimeoutSeconds())
                || Objects.isNull(runtime.getMaxModelCalls())
                || Objects.isNull(runtime.getMaxInputTokens())
                || Objects.isNull(toolPolicy)
                || Objects.isNull(toolPolicy.getMaxToolCalls())) {
            return;
        }
        if (generation.getMaxOutputTokens() > runtime.getMaxOutputTokens()) {
            add(issues, "OUTPUT_LIMIT_CONFLICT", "回答输出Token上限不能超过Agent输出上限");
        }
        if (generation.getTimeoutSeconds() > runtime.getTotalTimeoutSeconds()) {
            add(issues, "TIMEOUT_CONFLICT", "回答超时时间不能超过Agent总超时时间");
        }
        if (Boolean.TRUE.equals(rewrite.getEnabled())
                && rewrite.getTimeoutMillis() > runtime.getTotalTimeoutSeconds() * 1000) {
            add(issues, "REWRITE_TIMEOUT_CONFLICT", "改写超时时间不能超过Agent总超时时间");
        }
        if (Boolean.TRUE.equals(rewrite.getEnabled())
                && rewrite.getMaxHistoryTokens() >= runtime.getMaxInputTokens()) {
            add(issues, "REWRITE_INPUT_BUDGET_CONFLICT", "改写历史必须为当前问题和Prompt预留输入空间");
        }
        int requiredCalls = Boolean.TRUE.equals(rewrite.getEnabled()) ? 2 : 1;
        if (draft.getToolCodes() != null && !draft.getToolCodes().isEmpty()) {
            /*
             * 每次串行工具调用后都要再次调用模型，最后还需要一次模型调用生成自然语言回答。
             * 改写调用也计入 Agent 全局模型次数预算。
             */
            requiredCalls += toolPolicy.getMaxToolCalls();
        }
        if (runtime.getMaxModelCalls() < requiredCalls) {
            add(
                    issues,
                    "MODEL_CALL_LIMIT_CONFLICT",
                    "模型调用次数上限不足，当前配置至少需要" + requiredCalls + "次");
        }
        int memoryTokens = Boolean.TRUE.equals(memory.getEnabled()) ? memory.getMaxTokens() : 0;
        if (memoryTokens + retrieval.getMaxContextTokens() >= runtime.getMaxInputTokens()) {
            add(issues, "INPUT_BUDGET_CONFLICT", "记忆与检索上下文必须为用户问题和Prompt预留输入空间");
        }
        if (Objects.nonNull(chatModel)) {
            if (Objects.nonNull(chatModel.getMaxTokens())
                    && generation.getMaxOutputTokens() > chatModel.getMaxTokens()) {
                add(issues, "MODEL_OUTPUT_LIMIT_EXCEEDED", "回答输出上限超过模型单次输出上限");
            }
            if (Objects.nonNull(chatModel.getContextWindow())
                    && runtime.getMaxInputTokens() + generation.getMaxOutputTokens()
                    > chatModel.getContextWindow()) {
                add(issues, "MODEL_CONTEXT_LIMIT_EXCEEDED", "Agent输入输出预算超过模型上下文窗口");
            }
        }
        if (Boolean.TRUE.equals(rewrite.getEnabled())
                && Objects.nonNull(rewriteModel)) {
            if (Objects.nonNull(rewriteModel.getMaxTokens())
                    && rewrite.getMaxOutputTokens() > rewriteModel.getMaxTokens()) {
                add(issues, "REWRITE_MODEL_OUTPUT_LIMIT_EXCEEDED", "改写输出上限超过模型单次输出上限");
            }
            if (Objects.nonNull(rewriteModel.getContextWindow())
                    && rewrite.getMaxHistoryTokens() + rewrite.getMaxOutputTokens()
                    >= rewriteModel.getContextWindow()) {
                add(issues, "REWRITE_MODEL_CONTEXT_LIMIT_EXCEEDED", "改写历史与输出预算未给Prompt和当前问题预留上下文空间");
            }
        }
    }

    private static void add(
            List<AgentValidationIssueVO> issues, String code, String message) {
        boolean duplicated = issues.stream().anyMatch(
                issue -> issue.code().equals(code) && issue.message().equals(message));
        if (!duplicated) {
            issues.add(new AgentValidationIssueVO(code, message));
        }
    }
}
