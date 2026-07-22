package com.lingxi.workbench.admin.prompt.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.admin.prompt.common.constants.PromptVariableConstants;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import com.lingxi.workbench.admin.prompt.model.request.PreviewPromptRequest;
import com.lingxi.workbench.admin.prompt.model.request.PromptVariableRequest;
import com.lingxi.workbench.admin.prompt.model.vo.PreviewPromptVO;
import com.lingxi.workbench.admin.prompt.model.vo.AvailablePromptVariableVO;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.prompt.common.enums.BuiltInPromptVariable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Arrays;
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

    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile(PromptVariableConstants.VARIABLE_PLACEHOLDER_REGEXP);
    private static final Pattern VARIABLE_NAME_PATTERN =
            Pattern.compile(PromptVariableConstants.VARIABLE_NAME_REGEXP);

    /** 校验模板引用和变量Schema是否一致。 */
    public void validate(String promptContent, List<PromptVariableRequest> variables) {
        // 步骤1：校验声明的变量定义合法性并检查重复
        Set<String> declaredNames = new LinkedHashSet<>();
        for (PromptVariableRequest variable : variables) {
            validateVariableDefinition(variable);
            if (!declaredNames.add(variable.name())) {
                throw new BusinessException(
                        HttpStatusEnum.BAD_REQUEST, "变量重复声明：" + variable.name());
            }
        }

        // 步骤2：正则提取模板中引用的所有变量名
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

        // 步骤3：校验引用变量与声明变量双向一致
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
        // 建立变量名到声明信息的索引
        Map<String, PromptVariableRequest> declarations = request.variables().stream()
                .collect(Collectors.toMap(
                        PromptVariableRequest::name,
                        variable -> variable,
                        (left, right) -> left,
                        LinkedHashMap::new));

        // 解析测试值并进行类型校验，生成变量名到渲染值的映射
        Map<String, String> renderValues = new LinkedHashMap<>();
        declarations.forEach((name, declaration) -> {
            Object value = resolvePath(request.values(), name);
            if (declaration.required() && isMissing(value)) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "变量" + name + "不能为空");
            }
            if (!isMissing(value)) {
                validateValueType(name, declaration.type(), value, "变量值");
            }
            renderValues.put(name, renderValue(value));
        });

        // 执行模板变量替换
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
    public List<AvailablePromptVariableVO> availableVariables() {
        return Arrays.stream(BuiltInPromptVariable.values())
                .map(variable -> new AvailablePromptVariableVO(
                        variable.getCode(),
                        variable.getDisplayName(),
                        PromptVariableType.fromCode(variable.getType().getCode()),
                        variable.getDescribe()))
                .toList();
    }

    /** 校验变量Schema内部约束，确保从历史版本读取的数据也符合当前规范。 */
    private static void validateVariableDefinition(PromptVariableRequest variable) {
        if (variable.name() == null || !VARIABLE_NAME_PATTERN.matcher(variable.name()).matches()) {
            throw new BusinessException(
                    HttpStatusEnum.BAD_REQUEST,
                    "变量名称必须使用受支持的命名空间和小写点路径：" + variable.name());
        }
        if (!isMissing(variable.example())) {
            validateValueType(variable.name(), variable.type(), variable.example(), "示例值");
        }
    }

    /** 按点号分隔路径从嵌套Map中逐层解析测试变量值。 */
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

    /** 校验单个变量值与声明类型是否匹配。 */
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

    /** 校验整数值，Number和String均可接受。 */
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

    /** 校验长整数值，Number和String均可接受。 */
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

    /** 校验小数（BigDecimal）值，Number和String均可接受。 */
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

    /** 校验日期格式（yyyy-MM-dd）。 */
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

    /** 校验日期时间格式（ISO-8601 OffsetDateTime）。 */
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

    /** 将变量值渲染为字符串，String直接使用，其他类型转为JSON。 */
    private static String renderValue(Object rawValue) {
        if (isMissing(rawValue)) {
            return "";
        }
        return rawValue instanceof String stringValue
                ? stringValue
                : JSON.toJSONString(rawValue);
    }

    /** 判断变量值是否缺失（null或空白字符串）。 */
    private static boolean isMissing(Object value) {
        return value == null || value instanceof String stringValue && stringValue.isBlank();
    }

}
