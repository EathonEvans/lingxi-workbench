package com.lingxi.workbench.application.runtime.tool;

import com.lingxi.workbench.auth.model.entity.UserEntity;
import com.lingxi.workbench.auth.repo.mapper.UserMapper;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import jakarta.annotation.Resource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import static com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionMode.DETERMINISTIC_READ_ONLY;

/**
 * 查询指定时区当前时间的只读内置工具。
 *
 * <p>这里的定义用于执行器启动校验；正式调用时，应用运行端会使用 Tool 管理中的
 * 名称、描述和参数 Schema 装饰该回调。业务执行代码不依赖管理端模块。</p>
 */
@Component
public class GetCurrentUserInfoTool implements AgentTool {

    @Resource
    private UserMapper userMapper;

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
        return FunctionToolCallback.builder(definition.getCode(), this::execute)
                .description(definition.getDescription())
                .inputType(CurrentUserIdInput.class)
                .build();
    }

    private CurrentUserInfoOutput execute(CurrentUserIdInput input) {
        Long userId = input.userId;

        UserEntity userEntity = userMapper.selectById(userId);

        return new CurrentUserInfoOutput(userEntity.getDisplayName(), userEntity.getAvatarUrl());
    }

    private static ZoneId resolveZoneId(String requestedZoneId) {
        if (Objects.isNull(requestedZoneId) || requestedZoneId.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(requestedZoneId.trim());
        } catch (DateTimeException exception) {
            throw new AgentToolException("不支持的IANA时区：" + requestedZoneId, exception);
        }
    }

    /**
     * Spring AI 根据参数类型自动生成 Function parameters JSON Schema。
     */
    public record CurrentUserIdInput(
            @ToolParam(description = "当前用户ID，整数类型", required = true) Long userId) {
    }

    /**
     * Spring AI 将返回对象自动序列化为 Tool 消息 content。
     */
    public record CurrentUserInfoOutput(String username, String avatar) {
    }
}
