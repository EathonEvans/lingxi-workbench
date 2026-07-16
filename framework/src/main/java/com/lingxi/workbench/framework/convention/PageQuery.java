package com.lingxi.workbench.framework.convention;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 通用分页查询参数。
 *
 * <p>页码从 1 开始，单页最多查询 100 条，避免列表接口被异常参数拖垮。</p>
 */
public class PageQuery {

    @Min(value = 1, message = "页码不能小于1")
    private int current = 1;

    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private int size = 20;

    public int getCurrent() {
        return current;
    }

    public void setCurrent(int current) {
        this.current = current;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

}
