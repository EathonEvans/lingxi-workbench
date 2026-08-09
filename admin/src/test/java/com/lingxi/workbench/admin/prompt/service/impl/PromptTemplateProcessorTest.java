package com.lingxi.workbench.admin.prompt.service.impl;

import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import com.lingxi.workbench.admin.prompt.model.request.PreviewPromptRequest;
import com.lingxi.workbench.admin.prompt.model.request.PromptVariableRequest;
import com.lingxi.workbench.admin.prompt.model.vo.AvailablePromptVariableCatalogVO;
import com.lingxi.workbench.admin.prompt.model.vo.PreviewPromptVO;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void shouldRejectBuiltInVariableWithWrongType() {
        PromptVariableRequest invalidVariable = variable(
                "system.current_date",
                PromptVariableType.STRING,
                true);

        assertThrows(
                BusinessException.class,
                () -> processor.validate(
                        "当前日期：{{system.current_date}}",
                        List.of(invalidVariable)));
    }

    @Test
    void shouldApplySupportedNamespaceRuleConsistently() {
        List<String> namespaces = List.of(
                "system", "app", "agent", "collaboration", "user", "input",
                "session", "query", "workflow", "node", "tools");

        for (String namespace : namespaces) {
            String variableName = namespace + ".value";
            processor.validate(
                    "变量：{{" + variableName + "}}",
                    List.of(variable(variableName, PromptVariableType.STRING, true)));
        }
    }

    @Test
    void shouldExposeCompleteRuntimeVariableCatalogToPromptEditor() {
        AvailablePromptVariableCatalogVO catalog =
                processor.availableVariables();
        List<String> variableCodes = catalog.groups().stream()
                .flatMap(group -> group.variables().stream())
                .map(variable -> variable.getCode())
                .toList();

        /*
         * 前端只负责展示后端变量目录，变量能否进入具体角色 Prompt，
         * 以及是否由运行节点特殊消费，仍由后端契约决定。
        */
        assertTrue(variableCodes.contains("session.history"));
        assertTrue(variableCodes.contains("query.contextualization_history"));
        assertTrue(variableCodes.contains("collaboration.max_delegations"));
        assertFalse(variableCodes.contains("query.rewrite_history"));
        assertFalse(variableCodes.contains("knowledge.retrieval_query"));
        assertFalse(variableCodes.contains("knowledge.context"));
        assertEquals(
                "Agent协作",
                catalog.groups().stream()
                        .filter(group -> group.code().equals("collaboration"))
                        .findFirst()
                        .orElseThrow()
                        .displayName());
        assertEquals(
                "问题处理",
                catalog.groups().stream()
                        .filter(group -> group.code().equals("query"))
                        .findFirst()
                        .orElseThrow()
                        .displayName());
    }

    @Test
    void shouldAcceptContextualizationHistoryPlaceholder() {
        PromptVariableRequest contextualizationHistory = variable(
                "query.contextualization_history",
                PromptVariableType.MESSAGE_LIST,
                true);

        processor.validate(
                "历史消息：{{query.contextualization_history}}",
                List.of(contextualizationHistory));
    }

    @Test
    void shouldRejectVariableFromUnknownGroup() {
        PromptVariableRequest unknownVariable = variable(
                "unknown.value",
                PromptVariableType.STRING,
                true);

        assertThrows(
                BusinessException.class,
                () -> processor.validate(
                        "变量：{{unknown.value}}",
                        List.of(unknownVariable)));
    }

    private static PromptVariableRequest variable(
            String name,
            PromptVariableType type,
            boolean required) {
        return PromptVariableRequest.builder()
                .name(name)
                .type(type)
                .required(required)
                .description(name)
                .example(null)
                .build();
    }
}
