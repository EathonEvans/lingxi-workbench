package com.lingxi.workbench.agentruntime.gateway.advisor;

import com.lingxi.workbench.agentruntime.gateway.TokenUsageConverter;
import com.lingxi.workbench.agentruntime.model.ExecutionState;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** 使用 Spring AI Advisor 统一执行模型调用预算、开始/完成事件和实际 Token 记账。 */
@Component
public class RuntimeModelAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String FINAL_MODEL_CALL = "lingxi.runtime.final-model-call";

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ExecutionState state = RuntimeContextAdvisor.requireState(request);
        boolean finalCall = isFinalCall(request);
        int callNo = finalCall ? state.beforeFinalModelCall() : state.beforeModelCall();
        ChatClientResponse response = chain.nextCall(request);
        state.completeModelCall(callNo, usage(response), finalCall);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        ExecutionState state = RuntimeContextAdvisor.requireState(request);
        return Flux.defer(() -> {
            boolean finalCall = isFinalCall(request);
            int callNo = finalCall ? state.beforeFinalModelCall() : state.beforeModelCall();
            AtomicReference<TokenUsage> latestUsage = new AtomicReference<>(TokenUsage.empty());
            return chain.nextStream(request)
                    .doOnNext(response -> {
                        TokenUsage usage = usage(response);
                        if (usage.hasUsage()) {
                            latestUsage.set(usage);
                        }
                    })
                    .doOnComplete(() -> state.completeModelCall(callNo, latestUsage.get(), finalCall));
        });
    }

    @Override
    public String getName() {
        return RuntimeModelAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    private static TokenUsage usage(ChatClientResponse response) {
        if (Objects.isNull(response) || Objects.isNull(response.chatResponse())
                || Objects.isNull(response.chatResponse().getMetadata())) {
            return TokenUsage.empty();
        }
        return TokenUsageConverter.convertActual(response.chatResponse().getMetadata().getUsage());
    }

    private static boolean isFinalCall(ChatClientRequest request) {
        return Boolean.TRUE.equals(request.context().get(FINAL_MODEL_CALL));
    }
}
