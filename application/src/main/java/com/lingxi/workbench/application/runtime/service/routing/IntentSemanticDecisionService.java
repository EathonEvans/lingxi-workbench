package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.infra.ai.chat.spring.SpringAiTokenUsageConverter;
import com.lingxi.workbench.application.runtime.common.enums.IntentDecisionReason;
import com.lingxi.workbench.application.runtime.common.enums.IntentSemanticAction;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.ApplicationExecutionBudget;
import com.lingxi.workbench.application.runtime.model.IntentCandidate;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSlot;
import com.lingxi.workbench.application.runtime.model.IntentRecognizedSlot;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecision;
import com.lingxi.workbench.application.runtime.model.IntentSemanticDecisionResult;
import com.lingxi.workbench.application.runtime.model.IntentSemanticSlotValue;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.service.impl.PromptTemplateRenderer;
import com.lingxi.workbench.infra.ai.chat.SpringAiChatClientFactory;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;
import com.lingxi.workbench.infra.ai.chat.openai.OpenAiCompatibleChatModelFactory;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.workflow.model.CapabilityDefinition;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentMapping;
import com.lingxi.workbench.workflow.model.intent.ApplicationIntentBinding;
import com.lingxi.workbench.workflow.model.intent.IntentDecisionFailureAction;
import com.lingxi.workbench.workflow.model.intent.IntentExecutionTargetType;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentSlotValueType;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private static final int MAX_SLOT_STRING_CHARACTERS = 500;
    private final OpenAiCompatibleChatModelFactory chatModelFactory;
    private final SpringAiChatClientFactory chatClientFactory;
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final PromptRuntimeService promptRuntimeService;
    private final PromptTemplateRenderer promptRenderer;
    private final JsonMapper jsonMapper;
    private final IntentDecisionOutputConverter outputConverter;

    public IntentSemanticDecisionService(OpenAiCompatibleChatModelFactory chatModelFactory,
                                         SpringAiChatClientFactory chatClientFactory,
                                         ModelRuntimeConfigService modelRuntimeConfigService,
                                         PromptRuntimeService promptRuntimeService,
                                         PromptTemplateRenderer promptRenderer) {
        this.chatModelFactory = chatModelFactory;
        this.chatClientFactory = chatClientFactory;
        this.modelRuntimeConfigService = modelRuntimeConfigService;
        this.promptRuntimeService = promptRuntimeService;
        this.promptRenderer = promptRenderer;
        this.jsonMapper = strictJsonMapper();
        this.outputConverter = new IntentDecisionOutputConverter(jsonMapper);
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
        int maxOutputTokens = Objects.isNull(executionBudget)
                ? policy.maxOutputTokens()
                : Math.min(policy.maxOutputTokens(), executionBudget.remainingTokenBudget());
        ChatModelRuntimeConfig callConfig = decisionModelConfig(baseConfig, policy, maxOutputTokens);
        List<DecisionCandidate> decisionCandidates = decisionCandidates(
                context.getApplicationSnapshot(), version, candidates);
        String decisionInput = decisionInputJson(context, decisionCandidates);
        IntentDecisionAttemptAdvisor attemptAdvisor = new IntentDecisionAttemptAdvisor(executionBudget);
        int maxCalls = Math.min(1 + policy.maxRepeatAttempts(), remainingApplicationCalls);
        long startedAt = System.nanoTime();
        String validationMessage = null;
        RuntimeException lastFailure = null;

        while (attemptAdvisor.modelCalls() < maxCalls) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("意图语义决策线程已中断");
            }
            int remainingRepairs = Math.max(maxCalls - attemptAdvisor.modelCalls() - 1, 0);
            try {
                IntentSemanticDecision decision = call(publishedPrompt, decisionInput, validationMessage, callConfig,
                        Math.min(remainingRepairs, policy.maxRepeatAttempts()), attemptAdvisor);
                validateDecision(decision, candidates);
                return decisionResult(decision, policy, promptSnapshot, attemptAdvisor, startedAt);
            } catch (JacksonException | DecisionValidationException exception) {
                lastFailure = exception;
                validationMessage = safeValidationMessage(exception);
            } catch (RuntimeException exception) {
                if (hasInterruptedCause(exception)) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
                lastFailure = exception;
                break;
            }
        }

        return failedDecision(policy, promptSnapshot, attemptAdvisor, startedAt, lastFailure);
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

    private IntentSemanticDecision call(String publishedPrompt, String decisionInput, String validationMessage,
                                        ChatModelRuntimeConfig config, int repeatAttempts,
                                        IntentDecisionAttemptAdvisor attemptAdvisor) {
        OpenAiChatModel chatModel = chatModelFactory.create(config);
        String systemPrompt = publishedPrompt + INTENT_DECISION_PLATFORM_RULES;
        if (!config.isStructuredOutputSupported()) {
            systemPrompt = systemPrompt + "\n\n" + outputConverter.getFormat();
        }
        String userPrompt = "<intent_decision_input_json>\n" + decisionInput
                + "\n</intent_decision_input_json>";
        if (validationMessage != null) {
            userPrompt = userPrompt + INTENT_DECISION_REPAIR_INSTRUCTION + "\n上一次校验结果：" + validationMessage;
        }
        List<Message> messages = List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt));
        Prompt prompt = structuredPrompt(messages, chatModel.getOptions(), config);
        ChatClient.ChatClientRequestSpec requestSpec = chatClientFactory.create(chatModel)
                .prompt(prompt)
                .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
                .advisors(structuredOutputAdvisor(repeatAttempts), attemptAdvisor);
        ResponseEntity<ChatResponse, IntentSemanticDecision> response = requestSpec.call().responseEntity(outputConverter);
        return Objects.requireNonNull(response.entity(), "意图语义决策模型未返回结构化对象");
    }

    private StructuredOutputValidationAdvisor structuredOutputAdvisor(int repeatAttempts) {
        return StructuredOutputValidationAdvisor.builder()
                .advisorOrder(Ordered.HIGHEST_PRECEDENCE + 50)
                .outputJsonSchema(outputConverter.getJsonSchema())
                .jsonMapper(jsonMapper)
                .maxRepeatAttempts(repeatAttempts)
                .build();
    }

    private Prompt structuredPrompt(List<Message> messages, OpenAiChatOptions defaults,
                                    ChatModelRuntimeConfig config) {
        if (!config.isStructuredOutputSupported()) {
            return new Prompt(messages);
        }
        OpenAiChatModel.ResponseFormat responseFormat = OpenAiChatModel.ResponseFormat.builder()
                .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                .jsonSchema(outputConverter.getJsonSchema())
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

    private static void validateDecision(IntentSemanticDecision decision, List<IntentCandidate> candidates) {
        if (Objects.isNull(decision) || Objects.isNull(decision.getAction())
                || Objects.isNull(decision.getReasonCode())) {
            throw invalid("决策缺少action或reasonCode");
        }
        if (decision.getReasonCode() == IntentDecisionReason.SEMANTIC_DECISION_FAILED) {
            throw invalid("模型不能返回平台保留原因编码");
        }
        Map<String, IntentCandidate> candidatesByCode = candidates.stream()
                .collect(Collectors.toMap(IntentCandidate::intentCode, Function.identity()));
        switch (decision.getAction()) {
            case SELECT -> validateSelect(decision, candidatesByCode);
            case CLARIFY -> validateClarify(decision, candidatesByCode);
            case DEFER -> validateDefer(decision);
        }
    }

    private static void validateSelect(IntentSemanticDecision decision,
                                       Map<String, IntentCandidate> candidatesByCode) {
        requireCandidate(decision.getSelectedIntentCode(), candidatesByCode);
        requireEmpty(decision.alternativeIntentCodes(), "SELECT不能包含alternativeIntentCodes");
        if (decision.getReasonCode() != IntentDecisionReason.SINGLE_INTENT) {
            throw invalid("SELECT只能使用SINGLE_INTENT原因");
        }
        validateSlots(decision.recognizedSlots(), candidatesByCode.get(decision.getSelectedIntentCode()));
    }

    private static void validateClarify(IntentSemanticDecision decision,
                                        Map<String, IntentCandidate> candidatesByCode) {
        if (isBlank(decision.getSelectedIntentCode())) {
            requireEmpty(decision.recognizedSlots(), "候选歧义澄清不能携带recognizedSlots");
            List<String> alternatives = decision.alternativeIntentCodes();
            if (alternatives.size() < 2) {
                throw invalid("候选歧义澄清至少需要两个alternativeIntentCodes");
            }
            requireUniqueCandidates(alternatives, candidatesByCode);
            if (decision.getReasonCode() != IntentDecisionReason.AMBIGUOUS_INTENT
                    && decision.getReasonCode() != IntentDecisionReason.INSUFFICIENT_CONTEXT) {
                throw invalid("候选歧义澄清原因不合法");
            }
            return;
        }
        requireCandidate(decision.getSelectedIntentCode(), candidatesByCode);
        requireEmpty(decision.alternativeIntentCodes(), "缺槽位澄清不能包含alternativeIntentCodes");
        if (decision.getReasonCode() != IntentDecisionReason.MISSING_REQUIRED_SLOT) {
            throw invalid("已选择候选的CLARIFY只能表示缺少必填槽位");
        }
        IntentCandidate candidate = candidatesByCode.get(decision.getSelectedIntentCode());
        validateSlots(decision.recognizedSlots(), candidate);
        Set<String> recognizedCodes = decision.recognizedSlots().stream()
                .map(IntentRecognizedSlot::getSlotCode)
                .collect(Collectors.toSet());
        boolean hasMissingRequiredSlot = candidate.getSlots().stream()
                .anyMatch(slot -> slot.required() && !recognizedCodes.contains(slot.code()));
        if (!hasMissingRequiredSlot) {
            throw invalid("MISSING_REQUIRED_SLOT必须仍有未识别的必填槽位");
        }
    }

    private static void validateDefer(IntentSemanticDecision decision) {
        if (!isBlank(decision.getSelectedIntentCode()) || !decision.alternativeIntentCodes().isEmpty()
                || !decision.recognizedSlots().isEmpty()) {
            throw invalid("DEFER不能携带候选编码或槽位值");
        }
        Set<IntentDecisionReason> allowed = Set.of(IntentDecisionReason.OUT_OF_SCOPE,
                IntentDecisionReason.MULTIPLE_INTENTS, IntentDecisionReason.NEGATED_OR_CORRECTED,
                IntentDecisionReason.INSUFFICIENT_CONTEXT, IntentDecisionReason.NO_SUITABLE_CANDIDATE);
        if (!allowed.contains(decision.getReasonCode())) {
            throw invalid("DEFER原因不合法");
        }
    }

    private static void validateSlots(List<IntentRecognizedSlot> recognizedSlots, IntentCandidate candidate) {
        Map<String, IntentCandidateSlot> slotsByCode = candidate.getSlots().stream()
                .collect(Collectors.toMap(IntentCandidateSlot::code, Function.identity()));
        Set<String> recognizedCodes = new HashSet<>();
        for (IntentRecognizedSlot recognizedSlot : recognizedSlots) {
            if (Objects.isNull(recognizedSlot) || isBlank(recognizedSlot.getSlotCode())
                    || Objects.isNull(recognizedSlot.getValue())) {
                throw invalid("recognizedSlots存在不完整槽位");
            }
            if (!recognizedCodes.add(recognizedSlot.getSlotCode())) {
                throw invalid("recognizedSlots包含重复槽位编码");
            }
            IntentCandidateSlot slot = slotsByCode.get(recognizedSlot.getSlotCode());
            if (Objects.isNull(slot)) {
                throw invalid("模型返回了候选未声明的槽位");
            }
            validateSlotValue(recognizedSlot.getValue(), slot.valueType());
        }
    }

    private static void validateSlotValue(IntentSemanticSlotValue value, IntentSlotValueType expectedType) {
        int populated = countNonNull(value.getStringValue(), value.getNumberValue(),
                value.getDateValue(), value.getBooleanValue());
        if (populated != 1 || Objects.isNull(expectedType)) {
            throw invalid("槽位值必须且只能包含一个声明类型字段");
        }
        switch (expectedType) {
            case STRING -> {
                if (isBlank(value.getStringValue())
                        || value.getStringValue().length() > MAX_SLOT_STRING_CHARACTERS) {
                    throw invalid("STRING槽位值为空或超过长度上限");
                }
            }
            case NUMBER -> requireNonNull(value.getNumberValue(), "NUMBER槽位必须使用numberValue");
            case DATE -> requireNonNull(value.getDateValue(), "DATE槽位必须使用dateValue");
            case BOOLEAN -> requireNonNull(value.getBooleanValue(), "BOOLEAN槽位必须使用booleanValue");
        }
    }

    private IntentSemanticDecisionResult failedDecision(IntentRoutingPolicy policy,
                                                        PromptRuntimeSnapshot promptSnapshot,
                                                        IntentDecisionAttemptAdvisor attemptAdvisor,
                                                        long startedAt,
                                                        RuntimeException failure) {
        log.warn("意图语义决策失败，modelInstanceId={}，promptVersionId={}，modelCalls={}，errorType={}",
                policy.chatModelInstanceId(), promptSnapshot.promptVersionId(), attemptAdvisor.modelCalls(),
                Objects.isNull(failure) ? "UNKNOWN" : failure.getClass().getSimpleName());
        IntentSemanticDecision decision = IntentSemanticDecision.builder()
                .action(IntentSemanticAction.DEFER)
                .reasonCode(IntentDecisionReason.SEMANTIC_DECISION_FAILED)
                .alternativeIntentCodes(List.of())
                .recognizedSlots(List.of())
                .build();
        IntentSemanticDecisionResult result = decisionResult(
                decision, policy, promptSnapshot, attemptAdvisor, startedAt);
        if (policy.decisionFailureAction() == IntentDecisionFailureAction.FAIL) {
            throw new IntentSemanticDecisionException(result, failure);
        }
        return result;
    }

    private static IntentSemanticDecisionResult decisionResult(IntentSemanticDecision decision,
                                                               IntentRoutingPolicy policy,
                                                               PromptRuntimeSnapshot promptSnapshot,
                                                               IntentDecisionAttemptAdvisor attemptAdvisor,
                                                               long startedAt) {
        int modelCalls = attemptAdvisor.modelCalls();
        return IntentSemanticDecisionResult.builder()
                .decision(decision)
                .tokenUsage(attemptAdvisor.tokenUsage())
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
        return ChatModelRuntimeConfig.builder()
                .instanceId(source.getInstanceId())
                .baseUrl(source.getBaseUrl())
                .apiKey(source.getApiKey())
                .modelCode(source.getModelCode())
                .temperature(source.getTemperature())
                .topP(source.getTopP())
                .maxTokens(maxOutputTokens)
                .stopSequences(source.getStopSequences())
                .extensionParameters(source.getExtensionParameters())
                .timeoutSeconds(policy.timeoutSeconds())
                .maxRetries(source.getMaxRetries())
                .structuredOutputSupported(source.isStructuredOutputSupported())
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

    private static void requireUniqueCandidates(List<String> codes,
                                                Map<String, IntentCandidate> candidatesByCode) {
        if (new HashSet<>(codes).size() != codes.size()) {
            throw invalid("候选编码不能重复");
        }
        for (String code : codes) {
            requireCandidate(code, candidatesByCode);
        }
    }

    private static void requireCandidate(String code, Map<String, IntentCandidate> candidatesByCode) {
        if (isBlank(code) || !candidatesByCode.containsKey(code)) {
            throw invalid("模型选择了本次候选外编码");
        }
    }

    private static void requireEmpty(List<?> values, String message) {
        if (!values.isEmpty()) {
            throw invalid(message);
        }
    }

    private static void requireNonNull(Object value, String message) {
        if (Objects.isNull(value)) {
            throw invalid(message);
        }
    }

    private static int countNonNull(Object... values) {
        int count = 0;
        for (Object value : values) {
            if (Objects.nonNull(value)) {
                count++;
            }
        }
        return count;
    }

    private static String boundedText(String value, int maxCharacters) {
        String normalized = Objects.requireNonNullElse(value, "");
        return normalized.length() <= maxCharacters ? normalized : normalized.substring(0, maxCharacters);
    }

    private static boolean isBlank(String value) {
        return Objects.isNull(value) || value.isBlank();
    }

    private static String safeValidationMessage(RuntimeException exception) {
        return Optional.ofNullable(exception.getMessage())
                .filter(message -> !message.isBlank())
                .map(message -> message.length() > 300 ? message.substring(0, 300) : message)
                .orElse("结构化意图决策不符合平台协议");
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

    private static DecisionValidationException invalid(String message) {
        return new DecisionValidationException(message);
    }

    private static JsonMapper strictJsonMapper() {
        return JsonMapper.builder()
                .addModules(JacksonUtils.instantiateAvailableModules())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    /**
     * 意图决策协议使用平台维护的稳定 Schema，不再依赖 Spring AI 的 VicTools 代码生成。
     * Spring AI 仍负责结构化响应约束与重试，最终对象由严格 JsonMapper 解析。
     */
    private static final class IntentDecisionOutputConverter
            implements StructuredOutputConverter<IntentSemanticDecision> {

        private static final String JSON_SCHEMA = """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "properties": {
                    "action": {"type": "string", "enum": ["SELECT", "CLARIFY", "DEFER"]},
                    "selectedIntentCode": {"type": "string"},
                    "alternativeIntentCodes": {"type": "array", "items": {"type": "string"}},
                    "recognizedSlots": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "properties": {
                          "slotCode": {"type": "string"},
                          "value": {
                            "type": "object",
                            "additionalProperties": false,
                            "properties": {
                              "stringValue": {"type": "string"},
                              "numberValue": {"type": "number"},
                              "dateValue": {"type": "string", "format": "date"},
                              "booleanValue": {"type": "boolean"}
                            }
                          }
                        },
                        "required": ["slotCode", "value"]
                      }
                    },
                    "reasonCode": {
                      "type": "string",
                      "enum": ["SINGLE_INTENT", "AMBIGUOUS_INTENT", "MISSING_REQUIRED_SLOT",
                        "OUT_OF_SCOPE", "MULTIPLE_INTENTS", "NEGATED_OR_CORRECTED",
                        "INSUFFICIENT_CONTEXT", "NO_SUITABLE_CANDIDATE"]
                    }
                  },
                  "required": ["action", "reasonCode"]
                }
                """;

        private final JsonMapper jsonMapper;

        private IntentDecisionOutputConverter(JsonMapper jsonMapper) {
            this.jsonMapper = jsonMapper;
        }

        @Override
        public IntentSemanticDecision convert(String source) {
            return jsonMapper.readValue(source, IntentSemanticDecision.class);
        }

        @Override
        public String getFormat() {
            return "仅输出符合以下 JSON Schema 的 JSON 对象：\n" + JSON_SCHEMA;
        }

        @Override
        public String getJsonSchema() {
            return JSON_SCHEMA;
        }
    }

    /** 统计 StructuredOutputValidationAdvisor 内部修复产生的每一次真实模型调用。 */
    private static final class IntentDecisionAttemptAdvisor implements CallAdvisor {

        private final TokenUsageAccumulator tokenUsage = new TokenUsageAccumulator();
        private final ApplicationExecutionBudget executionBudget;
        private int modelCalls;

        private IntentDecisionAttemptAdvisor(ApplicationExecutionBudget executionBudget) {
            this.executionBudget = executionBudget;
        }

        @Override
        public String getName() {
            return "Intent Decision Attempt";
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE + 100;
        }

        @Override
        public synchronized ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            if (Objects.nonNull(executionBudget)) {
                executionBudget.reserveModelCall();
            }
            modelCalls++;
            ChatClientResponse response = chain.nextCall(request);
            TokenUsage usage = SpringAiTokenUsageConverter.convert(response.chatResponse());
            tokenUsage.add(usage);
            if (Objects.nonNull(executionBudget)) {
                executionBudget.recordUsage(usage);
            }
            return response;
        }

        private synchronized int modelCalls() {
            return modelCalls;
        }

        private TokenUsage tokenUsage() {
            return tokenUsage.snapshot();
        }
    }

    private static final class DecisionValidationException extends RuntimeException {

        private DecisionValidationException(String message) {
            super(message);
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
