package com.lingxi.workbench.chat.model.ccontext;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.variable.common.enums.BuiltInRuntimeVariable;
import com.lingxi.workbench.infra.ai.variable.common.enums.RuntimeVariableProducer;

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

    /**
     * 为受委派 Agent 创建独立的变量容器。
     *
     * <p>只复制请求入口已经冻结的用户、会话和时间变量，并将 user.query 替换为
     * Supervisor 下发的明确任务。历史、改写结果和知识上下文必须由子 Agent 自己的
     *处理链重新产生，不能复用父 Agent 的派生变量。</p>
     */
    public synchronized RuntimeVariables forkForAgentTask(String task) {
        return forkForQuery(task);
    }

    /**
     * 为路由结果创建新的执行变量，只替换当前任务问题。
     *
     * <p>共享历史、摘要和长期记忆由 ConversationContext 在 Agent 准备阶段重新投影，
     * 这里仅复制请求入口冻结的身份和时间事实，避免携带旧问题派生结果。</p>
     */
    public synchronized RuntimeVariables forkForQuery(String query) {
        Objects.requireNonNull(query, "执行问题不能为空");
        RuntimeVariables child = new RuntimeVariables();
        values.forEach((variable, value) -> {
            if (variable.getProducer() == RuntimeVariableProducer.REQUEST_INITIALIZATION && variable != BuiltInRuntimeVariable.USER_QUERY) {
                child.values.put(variable, value);
            }
        });
        child.put(RuntimeVariableAccessors.USER_QUERY, query);
        return child;
    }
}
