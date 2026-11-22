package com.lingxi.workbench.infra.ai.variable;

import com.lingxi.workbench.infra.ai.variable.common.enums.BuiltInRuntimeVariable;
import com.lingxi.workbench.infra.ai.variable.common.enums.RuntimeVariableGroup;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内置运行时变量的统一只读目录。
 *
 * <p>管理端展示、Prompt 语义校验和运行端取值都以此目录为入口，
 * 避免各端再维护变量编码或命名空间白名单。</p>
 */
public final class RuntimeVariableCatalog {

    private static final Map<String, BuiltInRuntimeVariable> VARIABLES_BY_CODE =
            Arrays.stream(BuiltInRuntimeVariable.values())
                    .collect(Collectors.toUnmodifiableMap(
                            BuiltInRuntimeVariable::getCode,
                            Function.identity()));
    private static final Map<String, RuntimeVariableGroup> GROUPS_BY_CODE =
            Arrays.stream(RuntimeVariableGroup.values())
                    .collect(Collectors.toUnmodifiableMap(
                            RuntimeVariableGroup::getCode,
                            Function.identity()));

    private RuntimeVariableCatalog() {
    }

    /** 按稳定编码查找内置运行时变量。 */
    public static Optional<BuiltInRuntimeVariable> find(String code) {
        return Optional.ofNullable(VARIABLES_BY_CODE.get(code));
    }

    /** 按稳定编码查找变量分组。 */
    public static Optional<RuntimeVariableGroup> findGroup(String code) {
        return Optional.ofNullable(GROUPS_BY_CODE.get(code));
    }

    /** 返回按展示顺序排列的变量分组。 */
    public static List<RuntimeVariableGroup> groups() {
        return Arrays.stream(RuntimeVariableGroup.values())
                .sorted(Comparator.comparingInt(RuntimeVariableGroup::getSort))
                .toList();
    }

    /** 返回指定分组中的内置变量，顺序与目录声明保持一致。 */
    public static List<BuiltInRuntimeVariable> variables(
            RuntimeVariableGroup group) {
        return Arrays.stream(BuiltInRuntimeVariable.values())
                .filter(variable -> variable.getGroup() == group)
                .toList();
    }
}
