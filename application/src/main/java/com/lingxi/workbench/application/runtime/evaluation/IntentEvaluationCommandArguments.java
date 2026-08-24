package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 离线命令的严格参数对象，避免将未校验的字符串直接传入业务服务。
 *
 * @param operation 命令操作
 * @param options   已解析的具名参数
 */
public record IntentEvaluationCommandArguments(
        IntentEvaluationCommandOperation operation,
        Map<String, String> options) {

    private static final Map<IntentEvaluationCommandOperation, Set<String>> ALLOWED_OPTIONS = Map.of(
            IntentEvaluationCommandOperation.EXPORT,
            Set.of("application-id", "application-version-id", "dataset-version", "output"),
            IntentEvaluationCommandOperation.EVALUATE,
            Set.of("dataset", "output"),
            IntentEvaluationCommandOperation.EXPORT_V2,
            Set.of("application-id", "application-version-id", "dataset-version", "output"),
            IntentEvaluationCommandOperation.EVALUATE_V2,
            Set.of("dataset", "output"));

    public IntentEvaluationCommandArguments {
        options = options == null ? Map.of() : Map.copyOf(options);
    }

    public static IntentEvaluationCommandArguments parse(String[] arguments) {
        if (arguments == null || arguments.length == 0) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "缺少意图评测命令参数");
        }
        IntentEvaluationCommandOperation operation;
        try {
            if (arguments[0] == null || arguments[0].isBlank()) {
                throw new IllegalArgumentException();
            }
            operation = IntentEvaluationCommandOperation.valueOf(
                    arguments[0].trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST,
                    "意图评测命令仅支持export、evaluate、export-v2或evaluate-v2");
        }
        Map<String, String> options = new HashMap<>();
        for (int index = 1; index < arguments.length; index++) {
            String argument = arguments[index];
            if (argument == null || !argument.startsWith("--") || !argument.contains("=")) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "意图评测命令参数格式应为--name=value");
            }
            int separator = argument.indexOf('=');
            String name = argument.substring(2, separator);
            String value = argument.substring(separator + 1);
            if (name.isBlank() || value.isBlank() || options.putIfAbsent(name, value) != null) {
                throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "意图评测命令参数非法或重复: " + argument);
            }
        }
        Set<String> unknownOptions = new TreeSet<>(options.keySet());
        unknownOptions.removeAll(ALLOWED_OPTIONS.get(operation));
        if (!unknownOptions.isEmpty()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "意图评测命令包含未知参数: " + unknownOptions);
        }
        return new IntentEvaluationCommandArguments(operation, options);
    }

    public String require(String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "缺少命令参数--" + name);
        }
        return value;
    }

    public Long requireLong(String name) {
        try {
            Long value = Long.valueOf(require(name));
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "命令参数--" + name + "必须为正整数");
        }
    }

    public Path requirePath(String name) {
        try {
            return Path.of(require(name));
        } catch (InvalidPathException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "命令参数--" + name + "不是合法路径");
        }
    }
}
