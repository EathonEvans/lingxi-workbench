package com.lingxi.workbench.application.runtime.tool;

import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.auth.model.entity.UserEntity;
import com.lingxi.workbench.auth.repo.mapper.UserMapper;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
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

        JSONObject output = invoke(42L);

        assertEquals("灵犀用户", output.getString("username"));
        assertEquals("http://storage.local/avatar.png", output.getString("avatar"));
        verify(userMapper).selectById(42L);
    }

    @Test
    void shouldFailWhenUserDoesNotExist() {
        when(userMapper.selectById(7L)).thenReturn(null);

        AgentToolException exception = assertThrows(AgentToolException.class, () -> invoke(7L));

        assertEquals("当前用户不存在，无法获取用户信息", exception.getMessage());
    }

    /** 参数固定为空 JSON，身份只能来自服务端可信执行上下文。 */
    private JSONObject invoke(Long userId) {
        AgentToolExecutionContext execution = AgentToolExecutionContext.builder()
                .executionId("test")
                .userId(userId)
                .build();
        String result = tool.execute(new AgentToolRequest("{}", execution, null));
        return JSONObject.parseObject(result);
    }
}
