package com.lingxi.workbench.framework.convention;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 通用分页查询参数。
 *
 * <p>页码从 1 开始，单页最多查询 100 条，避免列表接口被异常参数拖垮。</p>
 */
@Setter
@Getter
public class PageQuery {

    /**
     * 当前页码，从 1 开始。
     */
    @Min(value = 1, message = "页码不能小于1")
    private int current = 1;

    /**
     * 每页查询条数，最大 100。
     */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private int size = 20;


    /**
     * 转换为 MyBatis-Plus 分页对象，用于数据库分页查询。
     *
     * @param <T> 分页记录类型
     * @return MyBatis-Plus {@link Page} 对象
     */
    public <T> Page<T> toPage() {
        return new Page<>(current, size);
    }

}
