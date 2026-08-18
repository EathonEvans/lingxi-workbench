package com.lingxi.workbench.admin.agent.service;

import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import com.lingxi.workbench.admin.agent.model.entity.AgentDraftEntity;
import com.lingxi.workbench.admin.agent.model.entity.AgentDraftKnowledgeEntity;
import com.lingxi.workbench.admin.agent.model.entity.AgentEntity;
import com.lingxi.workbench.admin.agent.model.vo.AgentValidationIssueVO;
import com.lingxi.workbench.admin.agent.model.vo.AgentValidationResultVO;
import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.domain.model.entity.DomainEntity;
import com.lingxi.workbench.admin.domain.repo.DomainRepo;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeIndexVersionService;
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
import com.lingxi.workbench.framework.exceptions.BusinessException;
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
    private final KnowledgeIndexVersionService knowledgeIndexVersionService;
    private final DomainRepo domainRepo;
    private final AgentSkillBindingResolver skillBindingResolver;

    /** 对 Agent 及其草稿执行发布前完整校验：状态、模型、Prompt、知识库、策略跨字段限制。 */
    public AgentValidationResultVO validate(AgentEntity agent, AgentDraftEntity draft, AgentDraftKnowledgeEntity knowledgeBinding) {
        List<AgentValidationIssueVO> issues = new ArrayList<>();
        if (agent.getStatus() != EnableStatus.ACTIVE) {
            add(issues, "AGENT_DISABLED", "停用的Agent不能发布");
        }
        AgentRole role = agent.getRole();
        validateDomain(agent, issues);
        validatePolicies(role, draft, knowledgeBinding, issues);

        ModelInstanceEntity chatModel = validateModel(draft.getChatModelInstanceId(), ModelType.CHAT, "CHAT_MODEL", issues)
                .orElse(null);
        if (role == AgentRole.SPECIALIST
                && Objects.nonNull(chatModel)
                && (Objects.isNull(chatModel.getCapabilities())
                || !chatModel.getCapabilities().contains(ModelCapability.STREAMING))) {
            add(issues, "CHAT_MODEL_STREAMING_REQUIRED", "回答模型必须支持流式输出");
        }
        validateTools(role, draft, knowledgeBinding, chatModel, issues);
        validateSkills(role, draft, issues);
        validatePrompt(draft.getSystemPromptVersionId(), PromptRole.SYSTEM, RuntimeVariableChannel.SYSTEM_PROMPT, "SYSTEM_PROMPT", role, issues);
        if (role == AgentRole.SUPERVISOR) {
            if (Objects.nonNull(draft.getUserPromptVersionId())) {
                add(issues, "USER_PROMPT_UNSUPPORTED", "主Agent的规划输入由平台构建，不能配置USER Prompt");
            }
        } else {
            validatePrompt(draft.getUserPromptVersionId(), PromptRole.USER, RuntimeVariableChannel.USER_PROMPT, "USER_PROMPT", role, issues);
        }

        validateKnowledge(role, knowledgeBinding, issues);
        return new AgentValidationResultVO(issues.isEmpty(), issues);
    }

    /** 主Agent不执行领域 Skill；领域专家绑定项必须能在发布时锁定为可执行版本。 */
    private void validateSkills(AgentRole role, AgentDraftEntity draft, List<AgentValidationIssueVO> issues) {
        List<Long> skillIds = Objects.requireNonNullElse(draft.getSkillIds(), List.of());
        if (role == AgentRole.SUPERVISOR) {
            if (!skillIds.isEmpty()) {
                add(issues, "SKILL_UNSUPPORTED", "主Agent只负责规划和委派，不能绑定业务Skill");
            }
            return;
        }
        try {
            skillBindingResolver.resolve(skillIds, draft.getToolCodes());
        } catch (BusinessException exception) {
            add(issues, "SKILL_UNAVAILABLE", exception.getMessage());
        }
    }

    private void validatePolicies(AgentRole role, AgentDraftEntity draft, AgentDraftKnowledgeEntity knowledgeBinding, List<AgentValidationIssueVO> issues) {
        validatePolicy(draft.getGenerationPolicy(), "GENERATION_POLICY", issues);
        validatePolicy(draft.getToolPolicy(), "TOOL_POLICY", issues);
        if (role == AgentRole.SPECIALIST && Objects.nonNull(knowledgeBinding)) {
            validatePolicy(draft.getRetrievalPolicy(), "KNOWLEDGE_SEARCH_POLICY", issues);
        }
        if (role == AgentRole.SUPERVISOR) {
            validatePolicy(draft.getPlanPolicy(), "PLAN_POLICY", issues);
        } else if (Objects.nonNull(draft.getPlanPolicy())) {
            add(issues, "PLAN_POLICY_UNSUPPORTED", "仅主Agent可以配置计划策略");
        }
    }

    /** 领域专家必须属于启用领域，主Agent绑定领域时只能选择一级领域。 */
    private void validateDomain(AgentEntity agent, List<AgentValidationIssueVO> issues) {
        if (Objects.isNull(agent.getDomainId())) {
            if (agent.getRole() == AgentRole.SPECIALIST) {
                add(issues, "DOMAIN_REQUIRED", "领域专家必须选择所属领域");
            }
            return;
        }
        DomainEntity domain = domainRepo.findById(agent.getDomainId()).orElse(null);
        if (Objects.isNull(domain) || domain.getStatus() != EnableStatus.ACTIVE) {
            add(issues, "DOMAIN_UNAVAILABLE", "Agent所属领域不存在或已停用");
            return;
        }
        if (agent.getRole() == AgentRole.SUPERVISOR && domain.getDepth() != 1) {
            add(issues, "SUPERVISOR_DOMAIN_INVALID", "主Agent只能绑定一级领域或保持全局");
        }
    }

    /** 校验 Tool 已在控制面启用，并要求回答模型声明 TOOL_CALLING 能力。 */
    private void validateTools(AgentRole role, AgentDraftEntity draft, AgentDraftKnowledgeEntity knowledgeBinding, ModelInstanceEntity chatModel, List<AgentValidationIssueVO> issues) {
        List<String> toolCodes = draft.getToolCodes();
        if (Objects.isNull(toolCodes)) {
            add(issues, "TOOL_CODES_MISSING", "工具编码列表不能为空");
            return;
        }
        long distinctCount = toolCodes.stream().distinct().count();
        if (distinctCount != toolCodes.size()) {
            add(issues, "TOOL_CODE_DUPLICATED", "工具编码不能重复");
        }
        if (role == AgentRole.SUPERVISOR && !toolCodes.isEmpty()) {
            add(issues, "TOOL_CODES_UNSUPPORTED", "主Agent只负责规划和委派，不能绑定业务工具");
        }
        Set<String> activeCodes = toolService.findActiveCodes(toolCodes);
        for (String toolCode : toolCodes) {
            if (!activeCodes.contains(toolCode)) {
                add(issues, "TOOL_UNAVAILABLE", "Tool 不存在或已停用：" + toolCode);
            }
        }
        boolean toolCallingRequired = !toolCodes.isEmpty()
                || (role == AgentRole.SPECIALIST && Objects.nonNull(knowledgeBinding));
        if (toolCallingRequired
                && Objects.nonNull(chatModel)
                && (Objects.isNull(chatModel.getCapabilities())
                || !chatModel.getCapabilities().contains(ModelCapability.TOOL_CALLING))) {
            add(issues, "CHAT_MODEL_TOOL_CALLING_REQUIRED", "绑定Tool或知识搜索后，回答模型必须支持工具调用");
        }
    }

    private void validatePolicy(Object policy, String code, List<AgentValidationIssueVO> issues) {
        if (Objects.isNull(policy)) {
            add(issues, code + "_MISSING", "缺少" + code + "配置");
            return;
        }
        for (ConstraintViolation<Object> violation : beanValidator.validate(policy)) {
            add(issues, code + "_INVALID", violation.getMessage());
        }
    }

    private Optional<ModelInstanceEntity> validateModel(Long modelId, ModelType expectedType, String codePrefix, List<AgentValidationIssueVO> issues) {
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

    private void validatePrompt(Long versionId, PromptRole expectedRole, RuntimeVariableChannel variableChannel, String codePrefix, AgentRole role, List<AgentValidationIssueVO> issues) {
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
        validatePromptVariables(version, variableChannel, role, codePrefix, issues);
        if (expectedRole == PromptRole.USER) {
            validateRequiredUserVariables(version, role, codePrefix, issues);
        }
    }

    /**
     * 变量必须具备运行时来源，并且只能进入当前 Agent 能力对应的消费通道。
     *
     * <p>PromptRole 只描述模型消息角色，不能表达同为 SYSTEM 角色的普通回答指令
     * 和问题重写任务之间的变量差异，因此此处必须按 variableChannel 校验。</p>
     */
    private void validatePromptVariables(PromptVersionEntity version, RuntimeVariableChannel variableChannel, AgentRole role, String codePrefix, List<AgentValidationIssueVO> issues) {
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
            if (role != AgentRole.SUPERVISOR && variable == BuiltInRuntimeVariable.COLLABORATION_MAX_DELEGATIONS) {
                add(issues, codePrefix + "_VARIABLE_UNAVAILABLE", "仅Supervisor Agent提供协作变量：" + definition.getName());
                continue;
            }
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

    /** 所有 User Prompt 都必须消费用户问题。 */
    private static void validateRequiredUserVariables(PromptVersionEntity version, AgentRole role, String codePrefix, List<AgentValidationIssueVO> issues) {
        List<PromptVariableDefinition> variables = Objects.requireNonNullElse(version.getVariables(), List.of());
        List<String> variableNames = variables.stream()
                .map(PromptVariableDefinition::getName)
                .toList();
        requireUserVariable(variableNames, BuiltInRuntimeVariable.USER_QUERY, codePrefix, issues);
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

    private void validateKnowledge(AgentRole role, AgentDraftKnowledgeEntity binding, List<AgentValidationIssueVO> issues) {
        if (Objects.isNull(binding)) {
            return;
        }
        if (role != AgentRole.SPECIALIST) {
            add(issues, "KNOWLEDGE_UNSUPPORTED", role.getDescribe() + "不能绑定知识库");
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
        if (role == AgentRole.SPECIALIST) {
            try {
                knowledgeIndexVersionService.validatePublishable(
                        binding.getKnowledgeBaseId(), binding.getEmbeddingModelInstanceId());
            } catch (BusinessException exception) {
                add(issues, "KNOWLEDGE_INDEX_UNPUBLISHABLE", exception.getMessage());
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
