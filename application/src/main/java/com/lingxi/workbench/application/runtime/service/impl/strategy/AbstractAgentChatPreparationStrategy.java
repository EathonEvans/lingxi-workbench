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

/**
 * RAG_CHAT 和 SUPERVISOR 两类 Agent 共享的 Prompt 加载、记忆配置和持久化轮次上下文装配基类。
 *
 * <p>子类只需实现 {@link #agentType()} 返回类型标识和 {@link #prepare} 执行类型专属逻辑。
 * 通用能力由本类提供：</p>
 * <ul>
 *   <li><b>Prompt 加载</b>：通过 {@code PromptRuntimeService} 解析已发布的 System Prompt 和
 *       可选的 User Prompt（V2 schema 引入）</li>
 *   <li><b>记忆策略</b>：从 AgentRuntimeDefinition 中读取 memoryPolicy（开关、轮次、Token 上限），
 *       传递给 ModelChatContext 供后续记忆提取器消费</li>
 *   <li><b>运行时变量</b>：将 ChatExecutionContext 中初始化的运行时变量注入上下文</li>
 *   <li><b>Token 统计</b>：挂载 modelUsageRecorder 回调</li>
 *   <li><b>处理链</b>：通过构造函数注入的 ChatProcessChain 对上下文执行有序后处理</li>
 * </ul>
 *
 * <p>子类通过 {@code @Qualifier} 注入不同的 ChatProcessChain：
 * RAG_CHAT 走标准链（问题改写 → 检索 → 记忆提取 → 生成），
 * SUPERVISOR 走协作链（成员列表注入 → 委派工具注册 → 生成）。</p>
 */
public abstract class AbstractAgentChatPreparationStrategy implements AgentChatPreparationStrategy {

    private final PromptRuntimeService promptRuntimeService;
    private final ChatProcessChain processChain;

    protected AbstractAgentChatPreparationStrategy(
            PromptRuntimeService promptRuntimeService, ChatProcessChain processChain) {
        this.promptRuntimeService = promptRuntimeService;
        this.processChain = processChain;
    }

    /**
     * 构建两类 Agent 的通用基础上下文。
     *
     * <p>设置项：运行时变量、记忆开关/轮次/Token上限、System Prompt、
     * User Prompt（如有）、当前 Turn、Token 回调。</p>
     *
     * @param definition Agent 运行时定义快照
     * @param execution  对话执行上下文
     * @return 待子类进一步定制和处理的 ModelChatContext Builder
     */
    protected ModelChatContext.ModelChatContextBuilder commonContext(
            AgentRuntimeDefinition definition, ChatExecutionContext execution) {
        AgentRuntimeDefinition.MemoryPolicy memory = definition.getMemoryPolicy();
        return ModelChatContext.builder()
                .runtimeVariables(execution.getRuntimeVariables())
                .memoryEnabled(Optional.ofNullable(memory)
                        .map(AgentRuntimeDefinition.MemoryPolicy::enabled).orElse(null))
                .memoryMaxRounds(Optional.ofNullable(memory)
                        .map(AgentRuntimeDefinition.MemoryPolicy::maxRounds).orElse(null))
                .memoryMaxTokens(Optional.ofNullable(memory)
                        .map(AgentRuntimeDefinition.MemoryPolicy::maxTokens).orElse(null))
                .systemPrompt(promptRuntimeService.resolvePublished(
                        definition.resolveSystemPromptVersionId()))
                .userPrompt(resolveUserPrompt(definition).orElse(null))
                .turn(execution.getTurn())
                .modelUsageRecorder(execution::recordUsage);
    }

    /**
     * 通过注入的 ChatProcessChain 对上下文执行有序后处理。
     *
     * <p>处理链的具体行为取决于注入的 Qualifier：RAG_CHAT 链做检索和记忆提取，
     * SUPERVISOR 链做成员列表注入和委派工具注册。</p>
     *
     * @param context 已构建的上下文
     * @return 处理后的上下文
     */
    protected ModelChatContext process(ModelChatContext context) {
        processChain.process(context);
        return context;
    }

    /**
     * 解析 User Prompt（V2 schema 新增的可选角色 Prompt）。
     *
     * <p>Agent 草稿中可以不配置 User Prompt（userPromptVersionId 为 null），
     * 返回 Optional.empty()。配置了则异步解析已发布版本内容。</p>
     */
    private Optional<PromptRuntimeSnapshot> resolveUserPrompt(AgentRuntimeDefinition definition) {
        if (Objects.isNull(definition.getUserPromptVersionId())) {
            return Optional.empty();
        }
        return Optional.of(promptRuntimeService.resolvePublished(
                definition.getUserPromptVersionId()));
    }
}