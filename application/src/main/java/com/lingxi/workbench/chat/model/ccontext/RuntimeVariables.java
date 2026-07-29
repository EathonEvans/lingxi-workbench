package com.lingxi.workbench.chat.model.ccontext;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.variable.common.enums.BuiltInRuntimeVariable;

import java.util.EnumMap;
import java.util.Objects;
import java.util.Optional;

/**
 * 单次对话执行期间共享的运行时变量容器。
 *
 * <p>容器内部使用 EnumMap 保存异构值，但只允许通过类型安全访问器读写。
 * 同一个变量默认只能由其负责节点写入一次，以便从变量来源反推出节点顺序。</p>
 */
public final class RuntimeVariables {

    private final EnumMap<BuiltInRuntimeVariable, Object> values = new EnumMap<>(BuiltInRuntimeVariable.class);

    /**
     * 首次写入变量。
     *
     * @param accessor 变量访问器
     * @param value    已完成业务计算的变量值
     * @param <T>      变量对应的 Java 类型
     */
    public synchronized <T> void put(RuntimeVariableAccessor<T> accessor, T value) {
        Objects.requireNonNull(accessor, "accessor 不能为空");
        Objects.requireNonNull(value, "运行时变量值不能为空");
        T checkedValue = accessor.read(value);
        if (values.containsKey(accessor.variable())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "运行时变量禁止重复写入：" + accessor.variable().getCode());
        }
        values.put(accessor.variable(), checkedValue);
    }

    /**
     * 读取必须存在的变量。
     *
     * @param accessor 变量访问器
     * @param <T>      变量对应的 Java 类型
     * @return 已完成类型校验的变量值
     */
    public synchronized <T> T require(RuntimeVariableAccessor<T> accessor) {
        return find(accessor).orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "运行时变量尚未准备：" + accessor.variable().getCode()));
    }

    /**
     * 读取允许尚未产生的变量。
     *
     * @param accessor 变量访问器
     * @param <T>      变量对应的 Java 类型
     * @return 变量值；对应节点尚未执行时为空
     */
    public synchronized <T> Optional<T> find(RuntimeVariableAccessor<T> accessor) {
        Objects.requireNonNull(accessor, "accessor 不能为空");
        Object value = values.get(accessor.variable());
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(accessor.read(value));
    }

    /**
     * 按 Prompt 变量定义读取渲染文本。
     *
     * @param variable 内置 Prompt 变量
     * @return 已准备变量的渲染文本
     */
    public synchronized Optional<String> render(BuiltInRuntimeVariable variable) {
        Object value = values.get(variable);
        if (value == null) {
            return Optional.empty();
        }
        RuntimeVariableAccessor<?> accessor = RuntimeVariableAccessors.require(variable);
        return Optional.of(accessor.format(value));
    }
}
