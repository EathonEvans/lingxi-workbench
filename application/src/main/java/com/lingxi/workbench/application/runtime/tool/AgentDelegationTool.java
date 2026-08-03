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
 * 将已发布的成员 Agent 暴露为 Supervisor 可调用的动态工具。
 *
 * <p>它不是控制面 Tool 表中的业务工具，而是 Application 运行时根据成员白名单
 * 临时生成的委派适配器。模型只提交明确任务，Agent 身份和版本完全由服务端冻结。</p>
 */
public final class AgentDelegationTool implements AgentTool {

    private final AgentToolDefinition definition;
    private final Function<DelegateAgentInput, String> executor;

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

    private String execute(DelegateAgentInput input) {
        if (Objects.isNull(input) || Objects.isNull(input.getTask()) || input.getTask().isBlank()) {
            throw new AgentToolException("委派成员Agent时必须提供明确任务");
        }
        input.setTask(input.getTask().trim());
        return executor.apply(input);
    }

    private static String buildDescription(ApplicationAgentReference target) {
        String responsibility = Objects.requireNonNullElse(target.description(), "处理其专属领域问题");
        return "将一个边界明确的子任务委派给成员Agent【" + target.name() + "】。该成员职责：" + responsibility + "。仅在用户问题确实需要该职责时调用；task必须包含成员独立完成任务所需的上下文。";
    }

    /** Supervisor 提交给成员 Agent 的结构化输入。 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class DelegateAgentInput {
        /** 已消解指代并补全必要上下文的独立子任务。 */
        private String task;
    }
}
