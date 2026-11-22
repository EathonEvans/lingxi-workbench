package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.infra.ai.chat.spring.SpringAiTokenUsageConverter;
import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentSemanticAction;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ApplicationExecutionBudget;
import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSlot;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecision;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionResult;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.service.impl.PromptTemplateRenderer;
import com.lingxi.workbench.infra.ai.chat.SpringAiChatClientFactory;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;
import com.lingxi.workbench.infra.ai.chat.openai.OpenAiChatModelFactory;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.workflow.model.CapabilityDefinition;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.IntentDecisionFailureAction;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.lingxi.workbench.infra.ai.prompt.common.constants.PromptSupplementConstants.INTENT_DECISION_PLATFORM_RULES;
import static com.lingxi.workbench.infra.ai.prompt.common.constants.PromptSupplementConstants.INTENT_DECISION_REPAIR_INSTRUCTION;

/**
 * 使用 Spring AI typed structured output 在本次召回候选内完成语义决策。
 * 模型输出始终作为不可信输入，只有通过动作、候选和槽位校验后才会返回调用方。
 */
@Service
public class IntentSemanticDecisionService {

    private static final Logger log = LoggerFactory.getLogger(IntentSemanticDecisionService.class);
    private static final int MAX_HISTORY_MESSAGES = 6;
    private static final int MAX_HISTORY_CHARACTERS = 1_000;
    private static final int MAX_SUMMARY_CHARACTERS = 2_000;
    private final OpenAiChatModelFactory chatModelFactory;
    private final SpringAiChatClientFactory chatClientFactory;
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final PromptRuntimeService promptRuntimeService;
    private final PromptTemplateRenderer promptRenderer;
    private final JsonMapper jsonMapper;
    private final IntentDecisionOutputProtocol outputProtocol = new IntentDecisionOutputProtocol();

    public IntentSemanticDecisionService(OpenAiChatModelFactory chatModelFactory,
                                         SpringAiChatClientFactory chatClientFactory,
                                         ModelRuntimeConfigService modelRuntimeConfigService,
                                         PromptRuntimeService promptRuntimeService,
                                         PromptTemplateRenderer promptRenderer) {
        this.chatModelFactory = chatModelFactory;
        this.chatClientFactory = chatClientFactory;
        this.modelRuntimeConfigService = modelRuntimeConfigService;
        this.promptRuntimeService = promptRuntimeService;
        this.promptRenderer = promptRenderer;
        this.jsonMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();
    }

    /**
     * 使用锁定 IntentTreeVersion 的模型、Prompt 和策略完成一次候选限定决策。
     */
    public IntentSemanticDecisionResult decide(ConversationContext context,
                                               RuntimeIntentTreeVersionEntity version,
                                               List<IntentCandidate> candidates) {
        return decide(context, version, candidates, null);
    }

    /** Workflow 分类节点复用同一语义决策，并在每次真实调用时原子占用共享预算。 */
    public IntentSemanticDecisionResult decideForWorkflow(ConversationContext context,
                                                          RuntimeIntentTreeVersionEntity version,
                                                          List<IntentCandidate> candidates,
                                                          ApplicationExecutionBudget executionBudget) {
        return decide(context, version, candidates,
                Objects.requireNonNull(executionBudget, "Workflow意图分类缺少预算账本"));
    }

    private IntentSemanticDecisionResult decide(ConversationContext context,
                                                RuntimeIntentTreeVersionEntity version,
                                                List<IntentCandidate> candidates,
                                                ApplicationExecutionBudget executionBudget) {
        requireRequest(context, version, candidates);
        IntentTreeDefinition definition = version.getDefinitionSnapshot();
        IntentRoutingPolicy policy = Objects.requireNonNull(definition.routingPolicy(), "意图树缺少路由策略");
        // 路由不是最终执行器，必须至少给锁定目标或 fallback Agent 保留一次收口调用。
        int remainingApplicationCalls = Objects.isNull(executionBudget)
                ? Math.max(context.getApplicationSnapshot().definition().runtimePolicy().getMaxModelCalls()
                        - context.getPreparationModelCalls() - 1, 0)
                : Math.max(executionBudget.remainingModelCalls() - 1, 0);
        if (remainingApplicationCalls == 0
                || Objects.nonNull(executionBudget) && executionBudget.hasReachedTokenSoftLimit()) {
            return budgetExhaustedDecision(policy);
        }
        PromptRuntimeSnapshot promptSnapshot = promptRuntimeService.resolvePublished(policy.decisionPromptVersionId());
        String publishedPrompt = promptRenderer.renderIntentDecisionPrompt(promptSnapshot, context.getRuntimeVariables());
        ChatModelRuntimeConfig baseConfig = modelRuntimeConfigService.getChatModelConfig(policy.chatModelInstanceId());
        List<DecisionCandidate> decisionCandidates = decisionCandidates(
                context.getApplicationSnapshot(), version, candidates);
        String decisionInput = decisionInputJson(context, decisionCandidates);
        IntentDecisionAttempts attempts = new IntentDecisionAttempts(executionBudget);
        int maxCalls = Math.min(1 + policy.maxRepeatAttempts(), remainingApplicationCalls);
        long startedAt = System.nanoTime();
        String validationMessage = null;
        RuntimeException lastFailure = null;

        while (attempts.modelCalls < maxCalls) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("意图语义决策线程已中断");
            }
            if (Objects.nonNull(executionBudget) && executionBudget.shouldFinalize()) {
                break;
            }
            try {
                int maxOutputTokens = Objects.isNull(executionBudget) ? policy.maxOutputTokens()
                        : Math.min(policy.maxOutputTokens(), executionBudget.remainingTokenBudget());
                ChatModelRuntimeConfig callConfig = decisionModelConfig(baseConfig, policy, maxOutputTokens);
                String output = call(publishedPrompt, decisionInput, validationMessage, callConfig, attempts);
                IntentSemanticDecision decision = outputProtocol.read(output, candidates);
                return decisionResult(decision, policy, promptSnapshot, attempts, startedAt);
            } catch (IntentDecisionOutputProtocol.InvalidOutputException exception) {
                lastFailure = exception;
                validationMessage = exception.getMessage();
            } catch (RuntimeException exception) {
                if (hasInterruptedCause(exception)) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
                lastFailure = exception;
                break;
            }
        }

        return failedDecision(policy, promptSnapshot, attempts, startedAt, lastFailure);
    }

    /** 路由不得越过 Application 单轮总模型调用预算，也不为该降级额外调用模型。 */
    private static IntentSemanticDecisionResult budgetExhaustedDecision(IntentRoutingPolicy policy) {
        IntentSemanticDecision decision = IntentSemanticDecision.builder()
                .action(IntentSemanticAction.DEFER)
                .reasonCode(IntentDecisionReason.MODEL_BUDGET_EXHAUSTED)
                .alternativeIntentCodes(List.of())
                .recognizedSlots(List.of())
                .build();
        return IntentSemanticDecisionResult.builder()
                .decision(decision)
                .tokenUsage(TokenUsage.empty())
                .modelCalls(0)
                .retryCount(0)
                .modelInstanceId(policy.chatModelInstanceId())
                .promptVersionId(policy.decisionPromptVersionId())
                .durationMillis(0L)
                .build();
    }

    private String call(String publishedPrompt, String decisionInput, String validationMessage,
                        ChatModelRuntimeConfig config, IntentDecisionAttempts attempts) {
        OpenAiChatModel chatModel = chatModelFactory.create(config);
        String systemPrompt = publishedPrompt + INTENT_DECISION_PLATFORM_RULES;
        if (!config.isStructuredOutputSupported()) {
            systemPrompt = systemPrompt + "\n\n" + outputProtocol.getFormat();
        }
        String userPrompt = "<intent_decision_input_json>\n" + decisionInput
                + "\n</intent_decision_input_json>";
        if (Objects.nonNull(validationMessage)) {
            userPrompt = userPrompt + INTENT_DECISION_REPAIR_INSTRUCTION + "\n上一次校验结果：" + validationMessage;
        }
        List<Message> messages = List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt));
        Prompt prompt = structuredPrompt(messages, chatModel.getOptions(), config);
        ChatClient.ChatClientRequestSpec requestSpec = chatClientFactory.create(chatModel)
                .prompt(prompt)
                .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false));
        ChatResponse response = attempts.call(requestSpec);
        return Objects.isNull(response) || Objects.isNull(response.getResult())
                ? null : response.getResult().getOutput().getText();
    }

    private Prompt structuredPrompt(List<Message> messages, OpenAiChatOptions defaults,
                                    ChatModelRuntimeConfig config) {
        if (!config.isStructuredOutputSupported()) {
            return new Prompt(messages);
        }
        OpenAiChatModel.ResponseFormat responseFormat = OpenAiChatModel.ResponseFormat.builder()
                .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(outputProtocol.getJsonSchema())
                .build();
        return new Prompt(messages, defaults.mutate().responseFormat(responseFormat).build());
    }

    private String decisionInputJson(ConversationContext context, List<DecisionCandidate> candidates) {
        List<DecisionHistoryMessage> history = context.getRecentMessages().stream()
                .skip(Math.max(context.getRecentMessages().size() - MAX_HISTORY_MESSAGES, 0))
                .map(message -> new DecisionHistoryMessage(message.role().getCode(),
                        boundedText(message.content(), MAX_HISTORY_CHARACTERS)))
                .toList();
        DecisionQuery query = new DecisionQuery(context.getOriginalQuery(), context.routingQuery(),
                boundedText(context.getSummary(), MAX_SUMMARY_CHARACTERS), history);
        return jsonMapper.writeValueAsString(new IntentDecisionInput(query, candidates));
    }

    private static List<DecisionCandidate> decisionCandidates(AppRuntimeSnapshot application,
                                                               RuntimeIntentTreeVersionEntity version,
                                                               List<IntentCandidate> candidates) {
        ApplicationIntentBinding binding = application.definition().intentRouting();
        boolean isApplicationRoutingTree = Objects.nonNull(binding)
                && Objects.equals(binding.getIntentTreeId(), version.getIntentTreeId())
                && Objects.equals(binding.getIntentTreeVersionId(), version.getId());
        Map<String, ApplicationIntentMapping> mappings = isApplicationRoutingTree
                ? binding.mappings().stream().collect(Collectors.toMap(
                        ApplicationIntentMapping::getIntentCode, Function.identity()))
                : Map.of();
        Map<String, CapabilityDefinition> capabilities = application.definition().capabilityCatalog()
                .capabilities().stream()
                .collect(Collectors.toMap(CapabilityDefinition::getCode, Function.identity()));
        return candidates.stream().map(candidate -> {
            ApplicationIntentMapping mapping = mappings.get(candidate.intentCode());
            CapabilityDefinition capability = Objects.isNull(mapping)
                    ? null : capabilities.get(mapping.getCapabilityCode());
            IntentExecutionTargetType targetType = Objects.isNull(mapping) ? null : mapping.getTargetType();
            String capabilityName = targetType == IntentExecutionTargetType.FIXED_RESPONSE
                    ? "固定回复" : Objects.isNull(capability) ? null : capability.getName();
            String capabilityDescription = Objects.isNull(capability) ? null : capability.getDescription();
            CapabilityRiskLevel riskLevel = Objects.isNull(capability) ? null : capability.getRiskLevel();
            return DecisionCandidate.builder()
                    .intentCode(candidate.getIntentCode())
                    .name(candidate.getName())
                    .parentPath(candidate.getParentPath())
                    .description(candidate.getDescription())
                    .positiveExamples(candidate.getPositiveExamples())
                    .negativeExamples(candidate.getNegativeExamples())
                    .slots(candidate.getSlots())
                    .clarificationQuestion(candidate.getClarificationQuestion())
                    .similarity(candidate.getSimilarity())
                    .rank(candidate.getRank())
                    .capabilityCode(Objects.isNull(mapping) ? null : mapping.getCapabilityCode())
                    .capabilityName(capabilityName)
                    .capabilityDescription(capabilityDescription)
                    .targetType(targetType)
                    .riskLevel(riskLevel)
                    .build();
        }).toList();
    }

    private IntentSemanticDecisionResult failedDecision(IntentRoutingPolicy policy,
                                                        PromptRuntimeSnapshot promptSnapshot,
                                                        IntentDecisionAttempts attempts,
                                                        long startedAt,
                                                        RuntimeException failure) {
        log.warn("意图语义决策失败，modelInstanceId={}，promptVersionId={}，modelCalls={}，errorType={}",
                policy.chatModelInstanceId(), promptSnapshot.promptVersionId(), attempts.modelCalls,
                Objects.isNull(failure) ? "UNKNOWN" : failure.getClass().getSimpleName());
        IntentSemanticDecision decision = IntentSemanticDecision.builder()
                .action(IntentSemanticAction.DEFER)
                .reasonCode(IntentDecisionReason.SEMANTIC_DECISION_FAILED)
                .alternativeIntentCodes(List.of())
                .recognizedSlots(List.of())
                .build();
        IntentSemanticDecisionResult result = decisionResult(
                decision, policy, promptSnapshot, attempts, startedAt);
        if (policy.decisionFailureAction() == IntentDecisionFailureAction.FAIL) {
            throw new IntentSemanticDecisionException(result, failure);
        }
        return result;
    }

    private static IntentSemanticDecisionResult decisionResult(IntentSemanticDecision decision,
                                                               IntentRoutingPolicy policy,
                                                               PromptRuntimeSnapshot promptSnapshot,
                                                               IntentDecisionAttempts attempts,
                                                               long startedAt) {
        int modelCalls = attempts.modelCalls;
        return IntentSemanticDecisionResult.builder()
                .decision(decision)
                .tokenUsage(attempts.tokenUsage.snapshot())
                .modelCalls(modelCalls)
                .retryCount(Math.max(modelCalls - 1, 0))
                .modelInstanceId(policy.chatModelInstanceId())
                .promptVersionId(promptSnapshot.promptVersionId())
                .durationMillis((System.nanoTime() - startedAt) / 1_000_000)
                .build();
    }

    private static ChatModelRuntimeConfig decisionModelConfig(ChatModelRuntimeConfig source,
                                                              IntentRoutingPolicy policy,
                                                              int maxOutputTokens) {
        // SDK 隐式重试无法计入应用账本；该决策只允许外层有界的输出修复。
        return source.toBuilder()
                .maxRetries(0)
                .maxTokens(maxOutputTokens)
                .timeoutSeconds(policy.timeoutSeconds())
                .build();
    }

    private static void requireRequest(ConversationContext context,
                                       RuntimeIntentTreeVersionEntity version,
                                       List<IntentCandidate> candidates) {
        if (Objects.isNull(context) || Objects.isNull(version) || Objects.isNull(version.getId())
                || Objects.isNull(version.getDefinitionSnapshot()) || Objects.isNull(candidates)
                || candidates.isEmpty()) {
            throw new IllegalArgumentException("意图语义决策请求不完整");
        }
        Set<String> codes = new HashSet<>();
        for (IntentCandidate candidate : candidates) {
            if (Objects.isNull(candidate) || isBlank(candidate.intentCode()) || !codes.add(candidate.intentCode())) {
                throw new IllegalArgumentException("意图语义决策候选为空或编码重复");
            }
        }
    }

    private static String boundedText(String value, int maxCharacters) {
        String normalized = Objects.requireNonNullElse(value, "");
        return normalized.length() <= maxCharacters ? normalized : normalized.substring(0, maxCharacters);
    }

    private static boolean isBlank(String value) {
        return Objects.isNull(value) || value.isBlank();
    }

    private static boolean hasInterruptedCause(Throwable throwable) {
        Throwable current = throwable;
        while (Objects.nonNull(current)) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** 每次模型请求先占用预算，响应即记账，格式错误也不能抹掉已经发生的用量。 */
    private static final class IntentDecisionAttempts {

        private final TokenUsageAccumulator tokenUsage = new TokenUsageAccumulator();
        private final ApplicationExecutionBudget executionBudget;
        private int modelCalls;

        private IntentDecisionAttempts(ApplicationExecutionBudget executionBudget) {
            this.executionBudget = executionBudget;
        }

        private ChatResponse call(ChatClient.ChatClientRequestSpec request) {
            if (Objects.nonNull(executionBudget)) {
                executionBudget.reserveModelCall();
            }
            modelCalls++;
            ChatResponse response = request.call().chatResponse();
            TokenUsage usage = SpringAiTokenUsageConverter.convert(response);
            tokenUsage.add(usage);
            if (Objects.nonNull(executionBudget)) {
                executionBudget.recordUsage(usage);
            }
            return response;
        }
    }

    /**
     * @param query      当前原始问题、重写问题与有界会话上下文
     * @param candidates 候选元数据
     */
    private record IntentDecisionInput(DecisionQuery query, List<DecisionCandidate> candidates) {
    }

    /**
     * @param originalQuery 用户原始问题
     * @param routingQuery  路由使用的问题
     * @param summary       有界会话摘要
     * @param history       有界最近消息
     */
    private record DecisionQuery(String originalQuery, String routingQuery, String summary,
                                 List<DecisionHistoryMessage> history) {
    }

    /**
     * @param role    消息角色
     * @param content 有界消息正文
     */
    private record DecisionHistoryMessage(String role, String content) {
    }

    /** 发送给分类模型的候选及安全能力元数据，不包含任何目标主键或 Tool 参数。 */
    @Getter
    @Builder
    private static final class DecisionCandidate {

        private final String intentCode;
        private final String name;
        private final List<String> parentPath;
        private final String description;
        private final List<String> positiveExamples;
        private final List<String> negativeExamples;
        private final List<IntentCandidateSlot> slots;
        private final String clarificationQuestion;
        private final double similarity;
        private final int rank;
        private final String capabilityCode;
        private final String capabilityName;
        private final String capabilityDescription;
        private final IntentExecutionTargetType targetType;
        private final CapabilityRiskLevel riskLevel;
    }
}
