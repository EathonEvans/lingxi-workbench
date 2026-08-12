package com.lingxi.workbench.chat.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.application.runtime.service.app.RuntimeVariablesInitializer;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeVariableDefinition;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTemplateRendererTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T03:30:00Z"),
            ZoneId.of("Asia/Shanghai"));

    private final PromptTemplateRenderer renderer =
            new PromptTemplateRenderer();

    @Test
    void shouldRenderTrustedBuiltInVariablesFromRuntimeContext() {
        PromptRuntimeSnapshot snapshot = new PromptRuntimeSnapshot(
                88L,
                """
                        你运行在{{system.platform_name}}。
                        当前日期：{{system.current_date}}，时区：{{system.timezone}}。
                        用户ID={{user.id}}，会话ID={{session.id}}。
                        """,
                List.of(
                        definition("system.platform_name", "STRING"),
                        definition("system.current_date", "DATE"),
                        definition("system.timezone", "STRING"),
                        definition("user.id", "LONG"),
                        definition("session.id", "LONG")),
                PromptRole.SYSTEM);

        String rendered = renderer.renderSystemPrompt(snapshot, runtimeVariables());

        assertTrue(rendered.contains("你运行在Lingxi Workbench"));
        assertTrue(rendered.contains("当前日期：2026-07-28"));
        assertTrue(rendered.contains("时区：Asia/Shanghai"));
        assertTrue(rendered.contains("用户ID=7"));
        assertTrue(rendered.contains("会话ID=10"));
    }

    @Test
    void shouldRejectUserControlledVariableInSystemPrompt() {
        PromptRuntimeSnapshot snapshot = new PromptRuntimeSnapshot(
                88L,
                "请回答：{{user.query}}",
                List.of(definition("user.query", "STRING")),
                PromptRole.SYSTEM);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> renderer.renderSystemPrompt(snapshot, runtimeVariables()));

        assertTrue(exception.getMessage().contains(
                "系统提示词禁止使用变量：user.query"));
    }

    @Test
    void shouldRejectVariableWithoutRuntimeSource() {
        PromptRuntimeSnapshot snapshot = new PromptRuntimeSnapshot(
                88L,
                "应用名称：{{app.name}}",
                List.of(definition("app.name", "STRING")),
                PromptRole.SYSTEM);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> renderer.renderSystemPrompt(snapshot, runtimeVariables()));

        assertTrue(exception.getMessage().contains(
                "运行端不支持变量：app.name"));
    }

    @Test
    void shouldRejectRemovedKnowledgeContextVariable() {
        PromptRuntimeSnapshot snapshot = new PromptRuntimeSnapshot(
                89L,
                "资料：{{knowledge.context}}\n问题：{{user.query}}",
                List.of(
                        definition("knowledge.context", "STRING"),
                        definition("user.query", "STRING")),
                PromptRole.USER);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> renderer.renderUserPrompt(snapshot, runtimeVariables()));

        assertTrue(exception.getMessage().contains("运行端不支持变量：knowledge.context"));
    }

    @Test
    void shouldRenderCollaborationDelegationLimitInSystemPrompt() {
        RuntimeVariables variables = runtimeVariables();
        variables.put(RuntimeVariableAccessors.COLLABORATION_MAX_DELEGATIONS, 3);
        PromptRuntimeSnapshot snapshot = new PromptRuntimeSnapshot(
                91L,
                "本轮最多委派{{collaboration.max_delegations}}次。",
                List.of(definition("collaboration.max_delegations", "INTEGER")),
                PromptRole.SYSTEM);

        String rendered = renderer.renderSystemPrompt(snapshot, variables);

        assertEquals("本轮最多委派3次。", rendered);
    }

    @Test
    void shouldRenderContextualizationVariablesWithoutRelaxingSystemPrompt() {
        RuntimeVariables variables = runtimeVariables();
        variables.put(
                RuntimeVariableAccessors.CONTEXTUALIZATION_HISTORY,
                List.of(new ChatHistoryMessage(
                        ChatHistoryMessage.Role.USER,
                        "介绍一下 iPhone 16 Pro")));
        PromptRuntimeSnapshot snapshot = new PromptRuntimeSnapshot(
                90L,
                "历史：{{query.contextualization_history}}\n当前问题：{{user.query}}",
                List.of(
                        definition(
                                "query.contextualization_history",
                                "MESSAGE_LIST"),
                        definition("user.query", "STRING")),
                PromptRole.SYSTEM);

        String rendered = renderer.renderContextualizationPrompt(
                snapshot,
                variables);

        assertTrue(rendered.contains("介绍一下 iPhone 16 Pro"));
        assertTrue(rendered.contains("当前问题：如何退款"));
    }

    @Test
    void shouldRejectPromptRoleMismatch() {
        PromptRuntimeSnapshot snapshot = new PromptRuntimeSnapshot(
                89L,
                "问题：{{user.query}}",
                List.of(definition("user.query", "STRING")),
                PromptRole.USER);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> renderer.renderSystemPrompt(
                        snapshot,
                        runtimeVariables()));

        assertTrue(exception.getMessage().contains(
                "Prompt角色必须为SYSTEM"));
    }

    @Test
    void shouldDeserializePublishedVariableDeclarations() {
        List<PromptRuntimeVariableDefinition> definitions = JSON.parseArray(
                """
                        [
                          {
                            "name": "system.current_date",
                            "type": "DATE",
                            "required": true,
                            "description": "当前日期",
                            "example": "2026-07-28"
                          }
                        ]
                        """,
                PromptRuntimeVariableDefinition.class);

        assertEquals(
                new PromptRuntimeVariableDefinition(
                        "system.current_date",
                        "DATE",
                        true),
                definitions.getFirst());
    }

    private static PromptRuntimeVariableDefinition definition(
            String name,
            String type) {
        return new PromptRuntimeVariableDefinition(name, type, true);
    }

    private static RuntimeVariables runtimeVariables() {
        RuntimeVariablesInitializer initializer =
                new RuntimeVariablesInitializer(
                        FIXED_CLOCK,
                        "Lingxi Workbench",
                        Locale.SIMPLIFIED_CHINESE);
        RuntimeVariables variables = initializer.initialize(
                new LoginUser(7L, "tester", "测试用户", null),
                "如何退款",
                10L);
        variables.put(RuntimeVariableAccessors.SESSION_HISTORY, List.of());
        return variables;
    }
}
