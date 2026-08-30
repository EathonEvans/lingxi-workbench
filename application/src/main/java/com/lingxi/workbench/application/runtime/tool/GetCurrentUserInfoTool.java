package com.lingxi.workbench.application.runtime.tool;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.auth.model.entity.UserEntity;
import com.lingxi.workbench.auth.repo.mapper.UserMapper;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

import static com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionMode.DETERMINISTIC_READ_ONLY;

/**
 * 查询当前登录用户基本信息的只读内置工具。
 *
 * <p>用户主键不来自模型参数，而是从服务端可信执行上下文读取，
 * 防止模型通过工具参数伪造 userId 越权读取其他用户信息。</p>
 */
@Component
@RequiredArgsConstructor
public class GetCurrentUserInfoTool implements AgentTool {

    static final String DESCRIPTION = "仅当用户明确要求查询当前登录账号的显示名称或头像时调用。"
            + "仅返回显示名称和头像；不得用于获取简历、JD、候选人事实或回答通用求职问题。";

    private final UserMapper userMapper;

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.builder()
                .code("get_current_user_info")
                .displayName("获取当前用户信息")
                .description(DESCRIPTION)
                .executionMode(DETERMINISTIC_READ_ONLY)
                .build();
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of(), "additionalProperties", false);
    }

    @Override
    public String execute(AgentToolRequest request) {
        Long userId = request.execution().getUserId();
        UserEntity userEntity = userMapper.selectById(userId);
        if (Objects.isNull(userEntity)) {
            throw new AgentToolException("当前用户不存在，无法获取用户信息");
        }
        return JSON.toJSONString(new CurrentUserInfoOutput(
                userEntity.getDisplayName(), userEntity.getAvatarUrl()));
    }

    /**
     * 当前用户安全输出。
     *
     * @param username 用户显示名称
     * @param avatar   用户头像地址
     */
    public record CurrentUserInfoOutput(String username, String avatar) {
    }
}
