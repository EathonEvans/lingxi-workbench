package com.lingxi.workbench.framework.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工作流多态 JSON 类型元数据。
 *
 * <p>接口声明类型字段名和允许的具体配置类型，具体实现通过 typeName 声明
 * 稳定的业务编码。Gson 适配器据此完成工作流节点配置的序列化和反序列化。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JSONType {

    /** 多态 JSON 中保存具体类型编码的字段名。 */
    String typeKey() default "@type";

    /** 当前具体类型对外使用的稳定编码。 */
    String typeName() default "";

    /** 当前接口允许反序列化的具体实现列表。 */
    Class<?>[] seeAlso() default {};
}
