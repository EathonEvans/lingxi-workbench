package com.lingxi.workbench.application.runtime.tool;

import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.Objects;
import java.util.function.Function;

import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.DELEGATE_AGENT_TOOL_PREFIX;

/**
 * 将已发布的成员 Agent 暴露为 Supervisor LLM 可调用的动态 Function Tool。
 *
 * <p>它不是控制面 Tool 表（lingxi_core.ai_tool）中持久化的业务工具，
 * 而是 Application 运行时根据发布快照中的成员白名单临时生成的委派适配器。</p>
 *
 * <h2>为什么不用控制面 Tool</h2>
 * <ul>
 *   <li>成员 Agent 列表在 Application 发布时冻结，属于运行时快照数据</li>
 *   <li>每个成员 Agent 生成一个独立的委派工具，编码格式为
 *       {@code delegate_agent_}{agentId}</li>
 *   <li>委派行为涉及成员 Agent 的完整 ReAct 循环执行，不是简单的函数调用</li>
 *   <li>职责描述由 Agent 自己维护，委派工具自动获取最新描述</li>
 * </ul>
 *
 * <h2>安全约束</h2>
 * <ul>
 *   <li>模型只能提交任务文本（{@code task} 字段），Agent 身份和版本完全由服务端冻结</li>
 *   <li>{@link AgentCollaborationScope} 在每次委派前校验次数、深度和白名单</li>
 *   <li>任务文本不允许为空或纯空白，防止空委派</li>
 *   <li>委派工具编码使用 agentId 而非 agentName，避免名称变更导致工具失效</li>
 * </ul>
 *
 * <p>工具描述（description）由 {@link #buildDescription(ApplicationAgentReference)} 生成，
 * 格式为："将一个边界明确的子任务委派给成员Agent【名称】。该成员职责：...。仅在用户问题确实需要该职责时调用；
 * task必须包含成员独立完成任务所需的上下文。"</p>
 */
public final class AgentDelegationTool implements AgentTool {

    /** 工具定义（编码 + 展示名 + 描述） */
    private final AgentToolDefinition definition;

    /** 成员 Agent 的实际执行器，由运行端注入 */
    private final Function<DelegateAgentInput, String> executor;

    /**
     * 为指定成员 Agent 创建委派工具。
     *
     * @param target   发布快照中冻结的成员 Agent 引用（含名称和职责描述）
     * @param executor 成员 Agent 的实际执行器，接收 DelegateAgentInput，返回成员回答文本
     */
    public AgentDelegationTool(ApplicationAgentReference target, Function<DelegateAgentInput, String> executor) {
        this.definition = AgentToolDefinition.builder()
                .code(DELEGATE_AGENT_TOOL_PREFIX + target.agentId())
                .displayName("委派给" + target.name())
                .description(buildDescription(target))
                .build();
        this.executor = Objects.requireNonNull(executor, "成员Agent执行器不能为空");
    }

    @Override
    public AgentToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolCallback callback() {
        return FunctionToolCallback.builder(definition.getCode(), this::execute)
                .description(definition.getDescription())
                .inputType(DelegateAgentInput.class)
                .build();
    }

    /**
     * 执行委派：校验任务输入 → 调用成员 Agent 执行器 → 返回成员回答。
     *
     * <p>任务为空或纯空白时抛出 AgentToolException，
     * 该异常会被 ReAct 循环捕获并作为工具调用失败反馈给 Supervisor LLM。</p>
     *
     * @param input 包含 task 字段的委派输入
     * @return 成员 Agent 的完整回答文本
     */
    private String execute(DelegateAgentInput input) {
        if (Objects.isNull(input) || Objects.isNull(input.getTask()) || input.getTask().isBlank()) {
            throw new AgentToolException("委派成员Agent时必须提供明确任务");
        }
        input.setTask(input.getTask().trim());
        return executor.apply(input);
    }

    /**
     * 构建委派工具的 LLM 描述文本。
     *
     * <p>该描述直接注入 Supervisor 的 System Prompt 作为 Tool Definition，
     * 因此需要清晰表达：成员是谁、擅长什么、何时该用、task 怎么写。</p>
     */
    private static String buildDescription(ApplicationAgentReference target) {
        String responsibility = Objects.requireNonNullElse(target.description(), "处理其专属领域问题");
        return "将一个边界明确的子任务委派给成员Agent【" + target.name() + "】。该成员职责：" + responsibility
                + "。仅在用户问题确实需要该职责时调用；task必须包含成员独立完成任务所需的上下文。";
    }

    /**
     * Supervisor LLM 提交给成员 Agent 的结构化输入。
     *
     * <p>当前只有 task 一个字段——Supervisor 需要把子问题消解指代、
     * 补全上下文后写入。后续可扩展 priority、format、maxTokens 等字段。</p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class DelegateAgentInput {
        /** 已消解指代并补全必要上下文的独立子任务描述 */
        private String task;
    }
}