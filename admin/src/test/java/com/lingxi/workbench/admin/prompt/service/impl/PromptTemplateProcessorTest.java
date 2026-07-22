package com.lingxi.workbench.admin.prompt.service.impl;

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
                "agent.name", PromptVariableType.STRING, true);
        PromptVariableRequest language = variable(
                "input.language", PromptVariableType.STRING, true);

        PreviewPromptVO result = processor.preview(new PreviewPromptRequest(
                "你是{{agent.name}}，请使用{{input.language}}回答。",
                List.of(agentName, language),
                Map.of(
                        "agent", Map.of("name", "灵析助手"),
                        "input", Map.of("language", "中文"))));

        assertEquals("你是灵析助手，请使用中文回答。", result.renderedPrompt());
    }

    @Test
    void shouldSerializeObjectWithFastjson2() {
        PromptVariableRequest product = variable(
                "input.product", PromptVariableType.OBJECT, true);

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
    void shouldRejectInvalidVariableNameAsBusinessError() {
        PromptVariableRequest invalidVariable = variable(
                "product_name", PromptVariableType.STRING, true);

        assertThrows(
                BusinessException.class,
                () -> processor.validate("请生成商品文案", List.of(invalidVariable)));
    }

    @Test
    void shouldApplySupportedNamespaceRuleConsistently() {
        List<String> namespaces = List.of(
                "system", "app", "agent", "user", "input",
                "session", "workflow", "node", "tools", "knowledge");

        for (String namespace : namespaces) {
            String variableName = namespace + ".value";
            processor.validate(
                    "变量：{{" + variableName + "}}",
                    List.of(variable(variableName, PromptVariableType.STRING, true)));
        }
    }

    private static PromptVariableRequest variable(
            String name,
            PromptVariableType type,
            boolean required) {
        return new PromptVariableRequest(
                name,
                type,
                required,
                name,
                null);
    }
}
