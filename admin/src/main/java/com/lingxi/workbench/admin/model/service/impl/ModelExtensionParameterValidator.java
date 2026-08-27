package com.lingxi.workbench.admin.model.service.impl;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.framework.json.JSONArray;
import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.admin.model.common.constants.ModelExtensionParameterConstants;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;

import java.util.Locale;

/**
 * 模型实例扩展参数校验器。
 *
 * <p>扩展参数天然属于开放 JSON Schema，因此使用 JSONObject 表达边界数据；
 * 业务代码不直接使用 Map 传递未定义结构。</p>
 */
final class ModelExtensionParameterValidator {

    private ModelExtensionParameterValidator() {
    }

    /** 校验并返回与请求对象解除引用关系的 JSON 副本。 */
    static JSONObject normalize(JSONObject parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return new JSONObject();
        }
        String json = JSON.toJSONString(parameters);
        if (json.length() > ModelExtensionParameterConstants.MAX_JSON_LENGTH) {
            throw invalid("扩展参数不能超过16KB");
        }
        // 先标准化为 Gson 节点，再递归校验，避免嵌套 Map 绕过敏感字段检查。
        JSONObject normalizedParameters = JSON.parseObject(json);
        validateObject(normalizedParameters, 1);
        return normalizedParameters;
    }

    private static void validateObject(JSONObject object, int depth) {
        validateDepth(depth);
        for (String key : object.keySet()) {
            String normalizedKey = normalizeKey(key);
            if (depth == 1
                    && ModelExtensionParameterConstants.RESERVED_TOP_LEVEL_KEYS
                            .contains(normalizedKey)) {
                throw invalid("扩展参数禁止覆盖平台字段：" + key);
            }
            if (ModelExtensionParameterConstants.SENSITIVE_KEYS.contains(normalizedKey)) {
                throw invalid("扩展参数禁止保存敏感字段：" + key);
            }
            validateValue(object.get(key), depth + 1);
        }
    }

    private static void validateArray(JSONArray array, int depth) {
        validateDepth(depth);
        for (Object value : array) {
            validateValue(value, depth + 1);
        }
    }

    private static void validateValue(Object value, int depth) {
        if (value instanceof JSONObject object) {
            validateObject(object, depth);
            return;
        }
        if (value instanceof JSONArray array) {
            validateArray(array, depth);
        }
    }

    private static void validateDepth(int depth) {
        if (depth > ModelExtensionParameterConstants.MAX_NESTING_DEPTH) {
            throw invalid("扩展参数嵌套不能超过8层");
        }
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw invalid("扩展参数字段名不能为空");
        }
        return key.trim().toLowerCase(Locale.ROOT);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.BAD_REQUEST, message);
    }
}
