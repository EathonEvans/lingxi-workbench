package com.lingxi.workbench.framework.convention;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/**
 * 通用分页响应。
 *
 * <p>统一封装分页查询结果，包含当前页数据、分页参数和数据总数。
 * 通过 {@link #map(Function)} 可在保留分页元信息的前提下转换记录类型，
 * 通过 {@link #convert(IPage)} 可从 MyBatis-Plus 分页结果快速构建。</p>
 *
 * @param records 当前页数据
 * @param current 当前页码
 * @param size    每页条数
 * @param total   数据总数
 * @param <T>     数据类型
 */
public record PageResult<T>(List<T> records, int current, int size, long total) {

    /**
     * 紧凑构造器，对记录列表做不可变拷贝，防止外部修改。
     */
    public PageResult {
        records = List.copyOf(records);
    }

    /**
     * 在保留分页信息的同时转换当前页数据类型。
     *
     * @param converter 类型转换函数
     * @param <R>       目标数据类型
     * @return 类型转换后的分页结果
     */
    public <R> PageResult<R> map(Function<T, R> converter) {
        return new PageResult<>(records.stream().map(converter).toList(), current, size, total);
    }

    /**
     * 从 MyBatis-Plus 分页结果构建 {@link PageResult}。
     *
     * @param page MyBatis-Plus 分页对象
     * @param <T>  记录类型
     * @return 统一分页响应
     */
    public static <T> PageResult<T> convert(IPage<T> page) {
        return new PageResult<>(
                page.getRecords(),
                Math.toIntExact(page.getCurrent()),
                Math.toIntExact(page.getSize()),
                page.getTotal());
    }
}
