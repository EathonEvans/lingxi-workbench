package com.lingxi.workbench.agentruntime;

import com.lingxi.workbench.agentruntime.gateway.advisor.RuntimeContextAdvisor;
import com.lingxi.workbench.agentruntime.gateway.advisor.RuntimeModelAdvisor;
import com.lingxi.workbench.infra.ai.chat.SpringAiChatClientFactory;
import com.lingxi.workbench.agentruntime.model.AgentRuntimeLimitException;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.agentruntime.planner.PlannerClarification;
import com.lingxi.workbench.agentruntime.planner.PlannerDecision;
import com.lingxi.workbench.agentruntime.planner.PlannerDecisionAction;
import com.lingxi.workbench.agentruntime.planner.PlannerDecisionValidationException;
import com.lingxi.workbench.agentruntime.planner.PlannerDecisionValidator;
import com.lingxi.workbench.agentruntime.planner.PlannerInteractionType;
import com.lingxi.workbench.agentruntime.planner.PlannerRunRequest;
import com.lingxi.workbench.agentruntime.planner.PlannerRunResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.openai.OpenAiCompatibleChatModelFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Supervisor 决策生成、typed structured output、有限修复和业务校验的唯一 Runtime。
 */
@Component
public class PlannerRuntime {

    private static final String REPAIR_INSTRUCTION = """

            上一次结构化决策未通过平台协议或业务校验。请根据下方校验结果重新检查action与关联字段，
            返回一个全新的完整对象，不得解释错误，不得添加未声明字段。
            """;

    private final OpenAiCompatibleChatModelFactory chatModelFactory;
    private final SpringAiChatClientFactory chatClientFactory;
    private final RuntimeContextAdvisor runtimeContextAdvisor;
    private final RuntimeModelAdvisor runtimeModelAdvisor;
    private final PlannerDecisionValidator validator = new PlannerDecisionValidator();
    private final BeanOutputConverter<PlannerDecision> outputConverter;

    public PlannerRuntime(OpenAiCompatibleChatModelFactory chatModelFactory,
                          SpringAiChatClientFactory chatClientFactory,
                          RuntimeContextAdvisor runtimeContextAdvisor,
                          RuntimeModelAdvisor runtimeModelAdvisor) {
        this.chatModelFactory = chatModelFactory;
        this.chatClientFactory = chatClientFactory;
        this.runtimeContextAdvisor = runtimeContextAdvisor;
        this.runtimeModelAdvisor = runtimeModelAdvisor;
        this.outputConverter = new BeanOutputConverter<>(PlannerDecision.class, strictJsonMapper());
    }

    /**
     * 执行一次初始规划或重规划，所有协议修复均消耗同一 ExecutionState 预算。
     */
    public PlannerRunResult run(PlannerRunRequest request) {
        requireRequest(request);
        ExecutionState state = ExecutionState.builder()
                .runId(request.getRunId())
                .agentId(request.getAgentId())
                .agentVersionId(request.getAgentVersionId())
                .policy(request.getPolicy())
                .eventSink(request.getEventSink())
                .authorizedToolCodes(List.of())
                .initialSequenceNo(request.getInitialSequenceNo())
                .initialModelCalls(0)
                .initialToolCalls(0)
                .build();
        return run(request, state);
    }

    /** 在 Supervisor 共享状态中执行一轮规划，只返回本轮新增的模型与 Token 消耗。 */
    public PlannerRunResult run(PlannerRunRequest request, ExecutionState state) {
        requireRequest(request);
        Objects.requireNonNull(state, "Planner ExecutionState不能为空");
        int initialModelCalls = state.modelCalls();
        TokenUsageSnapshot initialUsage = new TokenUsageSnapshot(state.tokenUsage());
        state.planning(request.getPlanNo());
        int maxAttempts = Math.min(1 + request.getMaxProtocolRepairAttempts(),
                request.getPolicy().getMaxModelCalls());
        String validationMessage = null;
        for (int attemptNo = 1; attemptNo <= maxAttempts; attemptNo++) {
            String attemptPrompt = attemptNo == 1 ? request.getUserPrompt()
                    : request.getUserPrompt() + REPAIR_INSTRUCTION + "\n上一次校验结果：" + validationMessage;
            try {
                PlannerDecision decision = call(request, state, attemptPrompt);
                validator.validate(decision, request.getValidationContext());
                state.planCreated(request.getPlanNo(), decision);
                return result(decision, state, initialModelCalls, initialUsage);
            } catch (JacksonException | PlannerDecisionValidationException exception) {
                validationMessage = safeValidationMessage(exception);
            } catch (AgentRuntimeLimitException exception) {
                if ("AGENT_MODEL_CALL_LIMIT".equals(exception.getCode())
                        || "AGENT_TOKEN_LIMIT".equals(exception.getCode())) {
                    break;
                }
                throw exception;
            }
        }
        PlannerDecision fallback = safeClarification();
        state.planCreated(request.getPlanNo(), fallback);
        return result(fallback, state, initialModelCalls, initialUsage);
    }

    private static PlannerRunResult result(PlannerDecision decision, ExecutionState state,
                                           int initialModelCalls, TokenUsageSnapshot initialUsage) {
        return new PlannerRunResult(decision, initialUsage.delta(state.tokenUsage()),
                state.modelCalls() - initialModelCalls, state.lastSequenceNo());
    }

    private PlannerDecision call(PlannerRunRequest request, ExecutionState state, String userPrompt) {
        OpenAiChatModel chatModel = chatModelFactory.create(request.getModelConfig());
        List<Message> messages = new ArrayList<>();
        String systemPrompt = request.getModelConfig().isStructuredOutputSupported()
                ? request.getSystemPrompt()
                : request.getSystemPrompt() + "\n\n" + outputConverter.getFormat();
        messages.add(new SystemMessage(systemPrompt));
        request.getHistory().stream().map(message -> message.format()).forEach(messages::add);
        messages.add(new UserMessage(userPrompt));
        Prompt prompt = structuredPrompt(messages, chatModel.getOptions(), request.getModelConfig());
        ChatClient.ChatClientRequestSpec call = chatClientFactory.create(chatModel)
                .prompt(prompt)
                .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
                .advisors(spec -> spec
                        .advisors(runtimeContextAdvisor, structuredOutputAdvisor(request, state),
                                runtimeModelAdvisor)
                        .param(RuntimeContextAdvisor.EXECUTION_STATE, state)
                        .param(RuntimeModelAdvisor.FINAL_MODEL_CALL, false));
        ResponseEntity<ChatResponse, PlannerDecision> response = call.call().responseEntity(outputConverter);
        return Objects.requireNonNull(response.entity(), "Planner模型未返回结构化决策");
    }

    /**
     * 官方 Advisor 位于模型预算 Advisor 外层，确保其每次自修复都重新占用 Runtime 额度。
     */
    private StructuredOutputValidationAdvisor structuredOutputAdvisor(PlannerRunRequest request,
                                                                      ExecutionState state) {
        int remainingCalls = request.getPolicy().getMaxModelCalls() - state.modelCalls();
        int repeatAttempts = Math.min(request.getMaxProtocolRepairAttempts(),
                Math.max(remainingCalls - 1, 0));
        return StructuredOutputValidationAdvisor.builder()
                .advisorOrder(Ordered.HIGHEST_PRECEDENCE + 50)
                .outputJsonSchema(outputConverter.getJsonSchema())
                .jsonMapper(strictJsonMapper())
                .maxRepeatAttempts(repeatAttempts)
                .build();
    }

    /**
     * 只有模型实例明确声明 STRUCTURED_OUTPUT 时才发送供应商原生 JSON Schema。
     */
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

    private static String safeValidationMessage(RuntimeException exception) {
        return Optional.ofNullable(exception.getMessage())
                .filter(message -> !message.isBlank())
                .map(message -> message.length() > 300 ? message.substring(0, 300) : message)
                .orElse("结构化决策不符合平台协议");
    }

    private static PlannerDecision safeClarification() {
        return PlannerDecision.builder()
                .action(PlannerDecisionAction.CLARIFY)
                .clarification(PlannerClarification.builder()
                        .question("我暂时无法可靠判断下一步，请补充希望完成的具体目标。")
                        .options(List.of())
                        .interactionType(PlannerInteractionType.CLARIFICATION)
                        .capabilityCodes(List.of())
                        .build())
                .build();
    }

    private static void requireRequest(PlannerRunRequest request) {
        if (request == null || request.getPlanNo() == null || request.getPlanNo() < 1
                || request.getSystemPrompt() == null || request.getUserPrompt() == null
                || request.getModelConfig() == null || request.getValidationContext() == null
                || request.getPolicy() == null || request.getEventSink() == null
                || request.getMaxProtocolRepairAttempts() < 0) {
            throw new IllegalArgumentException("Planner Runtime请求不完整");
        }
    }

    private static JsonMapper strictJsonMapper() {
        return JsonMapper.builder()
                .addModules(JacksonUtils.instantiateAvailableModules())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    private record TokenUsageSnapshot(Integer inputTokens, Integer outputTokens, Integer totalTokens) {

        private TokenUsageSnapshot(com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage usage) {
            this(usage.inputTokens(), usage.outputTokens(), usage.totalTokens());
        }

        private com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage delta(
                com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage current) {
            return com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage.of(
                    subtract(current.inputTokens(), inputTokens),
                    subtract(current.outputTokens(), outputTokens),
                    subtract(current.totalTokens(), totalTokens));
        }

        private static Integer subtract(Integer current, Integer initial) {
            if (current == null && initial == null) {
                return null;
            }
            return Objects.requireNonNullElse(current, 0) - Objects.requireNonNullElse(initial, 0);
        }
    }
}
