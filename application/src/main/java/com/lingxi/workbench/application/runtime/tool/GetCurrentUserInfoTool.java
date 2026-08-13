package com.lingxi.workbench.application.runtime.tool;

import com.lingxi.workbench.auth.model.entity.UserEntity;
import com.lingxi.workbench.auth.repo.mapper.UserMapper;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.BiFunction;

import static com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionMode.DETERMINISTIC_READ_ONLY;

/**
 * 查询当前登录用户基本信息的只读内置工具。
 *
 * <p>用户主键不来自模型参数，而是从服务端可信 ToolContext 读取，
 * 防止模型通过工具参数伪造 userId 越权读取其他用户信息。</p>
 */
@Component
@RequiredArgsConstructor
public class GetCurrentUserInfoTool implements AgentTool {

    /** ToolContext 中当前登录用户主键的键名，与执行链路写入的上下文保持一致。 */
    private static final String USER_ID_CONTEXT_KEY = "userId";

    private final UserMapper userMapper;

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.builder()
                .code("get_current_user_info")
                .displayName("获取当前用户信息")
                .description("根据当前对话用户ID查询用户基本信息")
                .executionMode(DETERMINISTIC_READ_ONLY)
                .build();
    }

    @Override
    public ToolCallback callback() {
        AgentToolDefinition definition = definition();
        return FunctionToolCallback.builder(definition.getCode(),
                        (BiFunction<CurrentUserInfoInput, ToolContext, CurrentUserInfoOutput>) this::execute)
                .description(definition.getDescription())
                .inputType(CurrentUserInfoInput.class)
                .build();
    }

    private CurrentUserInfoOutput execute(CurrentUserInfoInput input, ToolContext toolContext) {
        Long userId = requireUserId(toolContext);
        UserEntity userEntity = userMapper.selectById(userId);
        if (Objects.isNull(userEntity)) {
            throw new AgentToolException("当前用户不存在，无法获取用户信息");
        }
        return new CurrentUserInfoOutput(userEntity.getDisplayName(), userEntity.getAvatarUrl());
    }

    /**
     * 从服务端可信执行上下文读取当前登录用户主键。
     *
     * <p>执行链路在构建 ToolContext 时写入登录态 userId，模型无法通过工具参数覆盖。</p>
     */
    private static Long requireUserId(ToolContext toolContext) {
        Object userId = toolContext.getContext().get(USER_ID_CONTEXT_KEY);
        if (Objects.isNull(userId) || !(userId instanceof Number number)) {
            throw new AgentToolException("缺少当前用户上下文，无法获取用户信息");
        }
        return number.longValue();
    }

    /**
     * Spring AI 根据参数类型自动生成 Function parameters JSON Schema。
     *
     * <p>本工具不需要模型提供任何参数，身份信息全部来自服务端可信上下文。</p>
     */
    public record CurrentUserInfoInput() {
    }

    /**
     * Spring AI 将返回对象自动序列化为 Tool 消息 content。
     *
     * @param username 用户显示名称
     * @param avatar   用户头像地址
     */
    public record CurrentUserInfoOutput(String username, String avatar) {
    }
}
