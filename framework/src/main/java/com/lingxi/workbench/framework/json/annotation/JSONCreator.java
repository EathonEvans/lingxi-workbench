package com.lingxi.workbench.framework.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 JSON 创建入口。
 *
 * <p>当前 Gson 适配层对普通枚举已能直接按业务值解析，因此该注解主要保留
 * 源码语义和未来扩展边界，不要求业务模块感知具体 JSON 库。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
public @interface JSONCreator {
}
