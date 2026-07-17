package com.lingxi.workbench.admin.prompt.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableOverflowStrategy;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableScope;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableSource;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import com.lingxi.workbench.admin.prompt.model.request.PreviewPromptRequest;
import com.lingxi.workbench.admin.prompt.model.request.PromptVariableRequest;
import com.lingxi.workbench.admin.prompt.model.vo.PreviewPromptVO;
import com.lingxi.workbench.admin.prompt.model.vo.PromptVariableVO;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Prompt变量Schema校验与模板预览处理器。 */
@Component
public class PromptTemplateProcessor {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "\\{\\{\\s*((?:system|app|agent|user|input|session|workflow|node|tools|knowledge)(?:\\.[a-z][a-z0-9_]*)+)\\s*}}");

    /** 校验模板引用和变量Schema是否一致。 */
    public void validate(String promptContent, List<PromptVariableRequest> variables) {
        Set<String> declaredNames = new LinkedHashSet<>();
        for (PromptVariableRequest variable : variables) {
            validateVariableDefinition(variable);
            if (!declaredNames.add(variable.name())) {
                throw new BusinessException(
                        HttpStatusEnum.BAD_REQUEST, "变量重复声明：" + variable.name());
            }
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(promptContent);
        Set<String> referencedNames = new LinkedHashSet<>();
        StringBuilder unmatched = new StringBuilder();
        while (matcher.find()) {
            referencedNames.add(matcher.group(1));
            matcher.appendReplacement(unmatched, "");
        }
        matcher.appendTail(unmatched);
        if (unmatched.indexOf("{{") >= 0 || unmatched.indexOf("}}") >= 0) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "Prompt中存在格式不正确的变量");
        }

        Set<String> undeclared = new LinkedHashSet<>(referencedNames);
        undeclared.removeAll(declaredNames);
        if (CollUtil.isNotEmpty(undeclared)) {
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST, "Prompt使用了未声明的变量：" + String.join("、", undeclared));
        }
        Set<String> unused = new LinkedHashSet<>(declaredNames);
        unused.removeAll(referencedNames);
        if (CollUtil.isNotEmpty(unused)) {
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST, "存在未使用的变量声明：" + String.join("、", unused));
        }
    }

    /** 使用按命名空间组织的测试数据渲染Prompt。 */
    public PreviewPromptVO preview(PreviewPromptRequest request) {
        validate(request.promptContent(), request.variables());
        Map<String, PromptVariableRequest> declarations = request.variables().stream()
                .collect(Collectors.toMap(
                        PromptVariableRequest::name,
                        variable -> variable,
                        (left, right) -> left,
                        LinkedHashMap::new));

        Map<String, String> renderValues = new LinkedHashMap<>();
        declarations.forEach((name, declaration) -> {
            Object suppliedValue = resolvePath(request.values(), name);
            Object value = isMissing(suppliedValue) ? declaration.defaultValue() : suppliedValue;
            if (declaration.required() && isMissing(value)) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "变量" + name + "不能为空");
            }
            if (!isMissing(value)) {
                validateValueType(name, declaration.type(), value, "变量值");
            }
            renderValues.put(name, renderValue(declaration, value));
        });

        Matcher matcher = VARIABLE_PATTERN.matcher(request.promptContent());
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(
                    rendered, Matcher.quoteReplacement(renderValues.get(matcher.group(1))));
        }
        matcher.appendTail(rendered);
        return new PreviewPromptVO(rendered.toString());
    }

    /** 查询当前平台已经具备运行时来源的内置变量。 */
    public List<PromptVariableVO> availableVariables() {
        return List.of(
                builtInVariable(
                        "system.current_time", "当前时间", PromptVariableType.DATETIME,
                        PromptVariableScope.SYSTEM, PromptVariableSource.SYSTEM, "当前请求时间，包含时区"),
                builtInVariable(
                        "system.current_date", "当前日期", PromptVariableType.DATE,
                        PromptVariableScope.SYSTEM, PromptVariableSource.SYSTEM, "当前请求日期"),
                builtInVariable(
                        "system.timezone", "系统时区", PromptVariableType.STRING,
                        PromptVariableScope.SYSTEM, PromptVariableSource.SYSTEM, "系统使用的标准时区"),
                builtInVariable(
                        "system.language", "系统语言", PromptVariableType.STRING,
                        PromptVariableScope.SYSTEM, PromptVariableSource.SYSTEM, "系统默认语言"),
                builtInVariable(
                        "system.platform_name", "平台名称", PromptVariableType.STRING,
                        PromptVariableScope.SYSTEM, PromptVariableSource.SYSTEM, "当前平台名称"),
                builtInVariable(
                        "user.id", "用户ID", PromptVariableType.LONG,
                        PromptVariableScope.USER, PromptVariableSource.USER_INPUT, "当前登录用户主键"),
                builtInVariable(
                        "user.name", "用户名称", PromptVariableType.STRING,
                        PromptVariableScope.USER, PromptVariableSource.USER_INPUT, "当前用户显示名称"),
                builtInVariable(
                        "user.query", "用户问题", PromptVariableType.STRING,
                        PromptVariableScope.USER, PromptVariableSource.USER_INPUT, "当前轮用户输入"),
                builtInVariable(
                        "session.id", "会话ID", PromptVariableType.LONG,
                        PromptVariableScope.SESSION, PromptVariableSource.SESSION_STATE, "当前会话主键"),
                builtInVariable(
                        "session.history", "会话历史", PromptVariableType.MESSAGE_LIST,
                        PromptVariableScope.SESSION, PromptVariableSource.SESSION_STATE,
                        "经过裁剪的会话消息列表"));
    }

    /** 校验变量Schema内部约束，确保从历史版本读取的数据也符合当前规范。 */
    private static void validateVariableDefinition(PromptVariableRequest variable) {
        PromptVariableScope expectedScope = scopeOf(variable.name());
        if (variable.scope() != expectedScope) {
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST,
                    "变量" + variable.name() + "的作用域应为" + expectedScope.getCode());
        }
        if (!supportsSource(variable.scope(), variable.source())) {
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST,
                    "变量" + variable.name() + "的来源与作用域不匹配");
        }
        if (!isMissing(variable.defaultValue())) {
            validateValueType(variable.name(), variable.type(), variable.defaultValue(), "默认值");
        }
        if (!isMissing(variable.example())) {
            validateValueType(variable.name(), variable.type(), variable.example(), "示例值");
        }
        if (variable.maxItems() != null
                && variable.type() != PromptVariableType.ARRAY
                && variable.type() != PromptVariableType.MESSAGE_LIST) {
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST,
                    "只有数组或消息列表变量可以设置最大元素数：" + variable.name());
        }
        if (variable.overflowStrategy() == PromptVariableOverflowStrategy.TOP_K
                && variable.maxItems() == null) {
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST,
                    "TOP_K策略必须设置最大元素数：" + variable.name());
        }
    }

    private static PromptVariableScope scopeOf(String variableName) {
        String namespace = variableName.substring(0, variableName.indexOf('.'));
        return switch (namespace) {
            case "system" -> PromptVariableScope.SYSTEM;
            case "app" -> PromptVariableScope.APPLICATION;
            case "agent" -> PromptVariableScope.AGENT;
            case "user" -> PromptVariableScope.USER;
            case "input" -> PromptVariableScope.INPUT;
            case "session" -> PromptVariableScope.SESSION;
            case "workflow" -> PromptVariableScope.WORKFLOW;
            case "node" -> PromptVariableScope.NODE;
            case "tools" -> PromptVariableScope.TOOL;
            case "knowledge" -> PromptVariableScope.KNOWLEDGE;
            default -> throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST, "变量命名空间不受支持：" + namespace);
        };
    }

    private static boolean supportsSource(
            PromptVariableScope scope, PromptVariableSource source) {
        return switch (scope) {
            case SYSTEM -> source == PromptVariableSource.SYSTEM;
            case APPLICATION -> source == PromptVariableSource.APPLICATION_CONFIG;
            case AGENT -> source == PromptVariableSource.AGENT_CONFIG;
            case USER -> source == PromptVariableSource.USER_INPUT;
            case INPUT -> source == PromptVariableSource.USER_INPUT
                    || source == PromptVariableSource.MANUAL;
            case SESSION -> source == PromptVariableSource.SESSION_STATE;
            case WORKFLOW -> source == PromptVariableSource.WORKFLOW_STATE;
            case NODE -> source == PromptVariableSource.NODE_STATE;
            case TOOL -> source == PromptVariableSource.TOOL_RESULT;
            case KNOWLEDGE -> source == PromptVariableSource.KNOWLEDGE_RESULT;
        };
    }

    private static Object resolvePath(Map<String, Object> values, String path) {
        Object current = values;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(segment);
        }
        return current;
    }

    private static void validateValueType(
            String variableName, PromptVariableType type, Object value, String valueLabel) {
        boolean valid = switch (type) {
            case STRING -> value instanceof String;
            case INTEGER -> isInteger(value);
            case LONG -> isLong(value);
            case DECIMAL -> isDecimal(value);
            case BOOLEAN -> value instanceof Boolean;
            case DATE -> isDate(value);
            case DATETIME -> isDateTime(value);
            case OBJECT -> value instanceof Map<?, ?>;
            case ARRAY, MESSAGE_LIST -> value instanceof Collection<?>;
            case DOCUMENT -> value instanceof String
                    || value instanceof Map<?, ?>
                    || value instanceof Collection<?>;
        };
        if (!valid) {
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST,
                    "变量" + variableName + "的" + valueLabel + "不符合" + type.getCode() + "类型");
        }
    }

    private static boolean isInteger(Object value) {
        if (!(value instanceof Number) && !(value instanceof String)) {
            return false;
        }
        try {
            new BigDecimal(value.toString()).intValueExact();
            return true;
        } catch (ArithmeticException | NumberFormatException exception) {
            return false;
        }
    }

    private static boolean isLong(Object value) {
        if (!(value instanceof Number) && !(value instanceof String)) {
            return false;
        }
        try {
            new BigDecimal(value.toString()).longValueExact();
            return true;
        } catch (ArithmeticException | NumberFormatException exception) {
            return false;
        }
    }

    private static boolean isDecimal(Object value) {
        if (!(value instanceof Number) && !(value instanceof String)) {
            return false;
        }
        try {
            new BigDecimal(value.toString());
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean isDate(Object value) {
        if (!(value instanceof String date)) {
            return false;
        }
        try {
            LocalDate.parse(date);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isDateTime(Object value) {
        if (!(value instanceof String dateTime)) {
            return false;
        }
        try {
            OffsetDateTime.parse(dateTime);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String renderValue(PromptVariableRequest definition, Object rawValue) {
        if (isMissing(rawValue)) {
            return "";
        }
        Object limitedValue = limitItems(definition, rawValue);
        String serializedValue = limitedValue instanceof String stringValue
                ? stringValue
                : JSON.toJSONString(limitedValue);
        if (definition.maxLength() == null
                || serializedValue.codePointCount(0, serializedValue.length())
                        <= definition.maxLength()) {
            return serializedValue;
        }
        if (definition.overflowStrategy() == PromptVariableOverflowStrategy.TRUNCATE) {
            int endIndex = serializedValue.offsetByCodePoints(0, definition.maxLength());
            return serializedValue.substring(0, endIndex);
        }
        throw new BusinessException(
                HttpStatusEnum.BAD_REQUEST,
                "变量" + definition.name() + "超过最大字符数" + definition.maxLength());
    }

    private static Object limitItems(PromptVariableRequest definition, Object value) {
        if (definition.maxItems() == null || !(value instanceof Collection<?> collection)) {
            return value;
        }
        if (collection.size() <= definition.maxItems()) {
            return value;
        }
        if (definition.overflowStrategy() == PromptVariableOverflowStrategy.TOP_K
                || definition.overflowStrategy() == PromptVariableOverflowStrategy.TRUNCATE) {
            return collection.stream().limit(definition.maxItems()).toList();
        }
        throw new BusinessException(
                HttpStatusEnum.BAD_REQUEST,
                "变量" + definition.name() + "超过最大元素数" + definition.maxItems());
    }

    private static boolean isMissing(Object value) {
        return value == null || value instanceof String stringValue && stringValue.isBlank();
    }

    private static PromptVariableVO builtInVariable(
            String name,
            String displayName,
            PromptVariableType type,
            PromptVariableScope scope,
            PromptVariableSource source,
            String description) {
        Integer maxItems = type == PromptVariableType.MESSAGE_LIST ? 20 : null;
        PromptVariableOverflowStrategy overflowStrategy = maxItems == null
                ? PromptVariableOverflowStrategy.REJECT
                : PromptVariableOverflowStrategy.TOP_K;
        return new PromptVariableVO(
                name, displayName, type, scope, source, true, null, description, null,
                20000, maxItems, 80, overflowStrategy, false, false);
    }
}
