package com.lingxi.workbench.admin.tool.service.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.admin.tool.common.enums.ToolParameterDataType;
import com.lingxi.workbench.admin.tool.model.config.ToolParameterDefinition;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.lingxi.workbench.admin.tool.common.constants.ToolConstants.MAX_PARAMETER_COUNT;

/**
 * Tool 参数定义与 Function Calling JSON Schema 的唯一转换入口。
 *
 * <p>管理端只维护易理解的字段结构；协议 Schema 由后端确定性生成并持久化。
 * 因为 Schema 结构受后端控制，也可以安全地还原为参数列表供再次编辑。</p>
 */
@Component
public class ToolParameterSchemaFactory {

    /** 根据不超过五项的结构化参数生成 OpenAI function.parameters。 */
    public JSONObject build(List<ToolParameterDefinition> parameters) {
        if (parameters.size() > MAX_PARAMETER_COUNT) {
            throw invalid("一个 Tool 最多配置 " + MAX_PARAMETER_COUNT + " 个参数");
        }
        ensureUniqueNames(parameters);
        JSONObject properties = new JSONObject();
        JSONArray required = new JSONArray();
        parameters.forEach(parameter -> appendParameter(properties, required, parameter));

        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    /** 将本服务生成的 Schema 还原成管理端参数定义。 */
    public List<ToolParameterDefinition> parse(JSONObject schema) {
        if (!"object".equals(schema.getString("type"))) {
            throw invalid("Tool 参数 Schema 顶层类型必须是 object");
        }
        JSONObject properties = schema.getJSONObject("properties");
        if (Objects.isNull(properties)) {
            throw invalid("Tool 参数 Schema 缺少 properties");
        }
        if (properties.size() > MAX_PARAMETER_COUNT) {
            throw invalid("一个 Tool 最多配置 " + MAX_PARAMETER_COUNT + " 个参数");
        }
        Set<String> requiredNames = requiredNames(schema.getJSONArray("required"));
        return properties.entrySet().stream()
                .map(entry -> toDefinition(entry.getKey(), entry.getValue(), requiredNames))
                .toList();
    }

    private static void ensureUniqueNames(List<ToolParameterDefinition> parameters) {
        Set<String> names = new HashSet<>();
        parameters.forEach(parameter -> {
            String normalizedName = parameter.getName().trim();
            if (!names.add(normalizedName)) {
                throw invalid("参数字段名不能重复：" + normalizedName);
            }
        });
    }

    private static void appendParameter(JSONObject properties, JSONArray required, ToolParameterDefinition parameter) {
        String name = parameter.getName().trim();
        JSONObject property = new JSONObject();
        property.put("type", parameter.getDataType().getSchemaType());
        property.put("description", parameter.getDescription().trim());
        properties.put(name, property);
        if (Boolean.TRUE.equals(parameter.getRequired())) {
            required.add(name);
        }
    }

    private static Set<String> requiredNames(JSONArray required) {
        if (Objects.isNull(required)) {
            return Set.of();
        }
        Set<String> names = new HashSet<>();
        required.forEach(value -> names.add(String.valueOf(value)));
        return names;
    }

    private static ToolParameterDefinition toDefinition(String name, Object propertyValue, Set<String> requiredNames) {
        JSONObject property;
        try {
            property = propertyValue instanceof JSONObject jsonObject ? jsonObject : JSONObject.from(propertyValue);
        } catch (RuntimeException exception) {
            throw invalid("Tool 参数 Schema 属性格式不正确：" + name);
        }
        ToolParameterDataType dataType = ToolParameterDataType.fromSchemaType(property.getString("type"))
                .orElseThrow(() -> invalid("Tool 参数类型暂不支持：" + property.getString("type")));
        String description = property.getString("description");
        if (Objects.isNull(description) || description.isBlank()) {
            throw invalid("Tool 参数缺少描述：" + name);
        }
        return ToolParameterDefinition.builder()
                .name(name)
                .description(description)
                .dataType(dataType)
                .required(requiredNames.contains(name))
                .build();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.BAD_REQUEST, message);
    }
}
