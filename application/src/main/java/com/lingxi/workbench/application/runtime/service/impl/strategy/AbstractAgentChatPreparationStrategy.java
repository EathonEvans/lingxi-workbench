package com.lingxi.workbench.application.runtime.service.impl.strategy;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.AgentChatPreparationStrategy;
import com.lingxi.workbench.application.runtime.service.PromptRuntimeService;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.service.ChatProcessChain;

import java.util.Objects;
import java.util.Optional;

/** 两类 Agent 共享的 Prompt、记忆和持久化轮次上下文装配。 */
public abstract class AbstractAgentChatPreparationStrategy implements AgentChatPreparationStrategy {

    private final PromptRuntimeService promptRuntimeService;
    private final ChatProcessChain processChain;

    protected AbstractAgentChatPreparationStrategy(PromptRuntimeService promptRuntimeService, ChatProcessChain processChain) {
        this.promptRuntimeService = promptRuntimeService;
        this.processChain = processChain;
    }

    protected ModelChatContext.ModelChatContextBuilder commonContext(AgentRuntimeDefinition definition, ChatExecutionContext execution) {
        AgentRuntimeDefinition.MemoryPolicy memory = definition.getMemoryPolicy();
        return ModelChatContext.builder()
                .runtimeVariables(execution.getRuntimeVariables())
                .memoryEnabled(Optional.ofNullable(memory).map(AgentRuntimeDefinition.MemoryPolicy::enabled).orElse(null))
                .memoryMaxRounds(Optional.ofNullable(memory).map(AgentRuntimeDefinition.MemoryPolicy::maxRounds).orElse(null))
                .memoryMaxTokens(Optional.ofNullable(memory).map(AgentRuntimeDefinition.MemoryPolicy::maxTokens).orElse(null))
                .systemPrompt(promptRuntimeService.resolvePublished(definition.resolveSystemPromptVersionId()))
                .userPrompt(resolveUserPrompt(definition).orElse(null))
                .turn(execution.getTurn())
                .modelUsageRecorder(execution::recordUsage);
    }

    protected ModelChatContext process(ModelChatContext context) {
        processChain.process(context);
        return context;
    }

    private Optional<PromptRuntimeSnapshot> resolveUserPrompt(AgentRuntimeDefinition definition) {
        if (Objects.isNull(definition.getUserPromptVersionId())) {
            return Optional.empty();
        }
        return Optional.of(promptRuntimeService.resolvePublished(definition.getUserPromptVersionId()));
    }
}
