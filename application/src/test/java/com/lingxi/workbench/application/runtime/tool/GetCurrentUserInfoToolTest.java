package com.lingxi.workbench.application.runtime.tool;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.auth.model.entity.UserEntity;
import com.lingxi.workbench.auth.repo.mapper.UserMapper;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.execution.ToolExecutionException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetCurrentUserInfoToolTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final GetCurrentUserInfoTool tool = new GetCurrentUserInfoTool(userMapper);

    @Test
    void shouldReadUserIdFromTrustedContextAndReturnUserInfo() {
        UserEntity user = new UserEntity();
        user.setDisplayName("灵犀用户");
        user.setAvatarUrl("http://storage.local/avatar.png");
        when(userMapper.selectById(42L)).thenReturn(user);

        JSONObject output = invoke(Map.of("userId", 42L));

        assertEquals("灵犀用户", output.getString("username"));
        assertEquals("http://storage.local/avatar.png", output.getString("avatar"));
        verify(userMapper).selectById(42L);
    }

    @Test
    void shouldFailWhenTrustedContextLacksUserId() {
        // 模型无论如何构造参数，都拿不到用户身份；缺少可信上下文时工具直接失败。
        ToolExecutionException exception = assertThrows(ToolExecutionException.class, () -> invoke(Map.of()));
        assertInstanceOf(AgentToolException.class, exception.getCause());
        verify(userMapper, never()).selectById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldFailWhenUserDoesNotExist() {
        when(userMapper.selectById(7L)).thenReturn(null);

        ToolExecutionException exception = assertThrows(ToolExecutionException.class,
                () -> invoke(Map.of("userId", 7L)));

        assertInstanceOf(AgentToolException.class, exception.getCause());
        assertEquals("当前用户不存在，无法获取用户信息", exception.getCause().getMessage());
    }

    /** 通过 Spring AI 协议层执行工具：参数固定为空 JSON，身份只能来自 ToolContext。 */
    private JSONObject invoke(Map<String, Object> context) {
        String result = tool.callback().call("{}", new ToolContext(context));
        return JSONObject.parseObject(result);
    }
}
