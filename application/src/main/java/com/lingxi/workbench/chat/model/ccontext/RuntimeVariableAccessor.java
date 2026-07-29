package com.lingxi.workbench.chat.model.ccontext;

import com.lingxi.workbench.infra.ai.variable.common.enums.BuiltInRuntimeVariable;

import java.util.Objects;
import java.util.function.Function;

/**
 * 运行时变量的类型安全访问器。
 *
 * <p>变量容器内部需要容纳不同 Java 类型，访问器把类型校验和文本渲染规则
 * 固定在变量定义处，业务节点因此无需进行强制类型转换。</p>
 *
 * @param variable       变量目录定义
 * @param valueReader    从容器值中读取并校验目标类型的函数
 * @param valueFormatter 将目标类型转换为 Prompt 文本的函数
 * @param <T>            变量对应的 Java 类型
 */
public record RuntimeVariableAccessor<T>(BuiltInRuntimeVariable variable,
                                         Function<Object, T> valueReader,
                                         Function<T, String> valueFormatter) {

    public RuntimeVariableAccessor {
        Objects.requireNonNull(variable, "variable 不能为空");
        Objects.requireNonNull(valueReader, "valueReader 不能为空");
        Objects.requireNonNull(valueFormatter, "valueFormatter 不能为空");
    }

    /**
     * 校验并读取变量值。
     *
     * @param value 容器中保存的原始值
     * @return 已完成类型校验的变量值
     */
    T read(Object value) {
        return valueReader.apply(value);
    }

    /**
     * 将变量值转换为 Prompt 模板可替换的文本。
     *
     * @param value 容器中保存的原始值
     * @return 模板替换文本
     */
    String format(Object value) {
        return valueFormatter.apply(read(value));
    }
}
