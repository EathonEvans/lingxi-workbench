package com.lingxi.workbench.framework.convention;

import java.util.List;
import java.util.function.Function;

/**
 * 通用分页响应。
 *
 * @param records 当前页数据
 * @param current 当前页码
 * @param size    每页条数
 * @param total   数据总数
 * @param <T>     数据类型
 */
public record PageResult<T>(List<T> records, int current, int size, long total) {

    public PageResult {
        records = List.copyOf(records);
    }

    /** 在保留分页信息的同时转换当前页数据类型。 */
    public <R> PageResult<R> map(Function<T, R> converter) {
        return new PageResult<>(records.stream().map(converter).toList(), current, size, total);
    }
}
