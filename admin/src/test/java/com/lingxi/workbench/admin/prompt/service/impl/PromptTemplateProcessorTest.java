package com.lingxi.workbench.admin.prompt.service.impl;

import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableOverflowStrategy;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableScope;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableSource;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import com.lingxi.workbench.admin.prompt.model.request.PreviewPromptRequest;
import com.lingxi.workbench.admin.prompt.model.request.PromptVariableRequest;
import com.lingxi.workbench.admin.prompt.model.vo.PreviewPromptVO;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromptTemplateProcessorTest {

    private final PromptTemplateProcessor processor = new PromptTemplateProcessor();

    @Test
    void shouldRenderNestedVariables() {
        PromptVariableRequest agentName = variable(
                "agent.name", "Agent名称", PromptVariableType.STRING,
                PromptVariableScope.AGENT, PromptVariableSource.AGENT_CONFIG, true, null);
        PromptVariableRequest language = variable(
                "input.language", "回答语言", PromptVariableType.STRING,
                PromptVariableScope.INPUT, PromptVariableSource.USER_INPUT, false, "中文");

        PreviewPromptVO result = processor.preview(new PreviewPromptRequest(
                "你是{{agent.name}}，请使用{{input.language}}回答。",
                List.of(agentName, language),
                Map.of("agent", Map.of("name", "灵析助手"))));

        assertEquals("你是灵析助手，请使用中文回答。", result.renderedPrompt());
    }

    @Test
    void shouldSerializeObjectWithFastjson2() {
        PromptVariableRequest product = variable(
                "input.product", "商品信息", PromptVariableType.OBJECT,
                PromptVariableScope.INPUT, PromptVariableSource.USER_INPUT, true, null);

        PreviewPromptVO result = processor.preview(new PreviewPromptRequest(
                "商品：{{input.product}}",
                List.of(product),
                Map.of("input", Map.of("product", Map.of("name", "键盘")))));

        assertEquals("商品：{\"name\":\"键盘\"}", result.renderedPrompt());
    }

    @Test
    void shouldRejectUndeclaredVariable() {
        assertThrows(
                BusinessException.class,
                () -> processor.validate("请回答{{user.query}}", List.of()));
    }

    @Test
    void shouldRejectScopeThatDoesNotMatchNamespace() {
        PromptVariableRequest invalidVariable = variable(
                "user.query", "用户问题", PromptVariableType.STRING,
                PromptVariableScope.INPUT, PromptVariableSource.USER_INPUT, true, null);

        assertThrows(
                BusinessException.class,
                () -> processor.validate("请回答{{user.query}}", List.of(invalidVariable)));
    }

    private static PromptVariableRequest variable(
            String name,
            String displayName,
            PromptVariableType type,
            PromptVariableScope scope,
            PromptVariableSource source,
            boolean required,
            Object defaultValue) {
        return new PromptVariableRequest(
                name,
                displayName,
                type,
                scope,
                source,
                required,
                defaultValue,
                displayName,
                null,
                2000,
                type == PromptVariableType.ARRAY ? 20 : null,
                50,
                PromptVariableOverflowStrategy.REJECT,
                false,
                source == PromptVariableSource.USER_INPUT);
    }
}
