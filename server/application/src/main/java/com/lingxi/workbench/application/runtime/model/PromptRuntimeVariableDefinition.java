package com.lingxi.workbench.application.runtime.model;

/**
 * 已发布 PromptVersion 中单个变量声明的运行时只读契约。
 *
 * @param name     变量稳定编码
 * @param type     变量数据类型编码
 * @param required 运行时是否必须提供非空值
 */
public record

PromptRuntimeVariableDefinition(
        String name,
        String type,
        boolean required) {

    public boolean valid() {
        return name() == null || name().isBlank() || type() == null || type().isBlank();
    }
}
