package com.lingxi.workbench.framework.json.annotation;

import com.lingxi.workbench.framework.json.JSONWriter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 项目 JSON 字段元数据。
 *
 * <p>该注解只承载现有代码需要的字段别名、枚举值字段和 Long 字符串化策略，
 * 由 Gson 适配层解释，不再依赖外部 JSON 供应商的注解实现。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface JSONField {

    /** 对外 JSON 字段名；为空时沿用 Java 字段名。 */
    String name() default "";

    /** 是否使用枚举内部的业务值字段作为 JSON 值。 */
    boolean value() default false;

    /** 当前字段需要启用的输出特性。 */
    JSONWriter.Feature[] serializeFeatures() default {};
}
