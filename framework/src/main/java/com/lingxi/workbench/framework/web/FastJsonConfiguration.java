package com.lingxi.workbench.framework.web;

import com.alibaba.fastjson2.support.config.FastJsonConfig;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 所有启动模块共用的 Fastjson2 序列化配置。
 *
 * <p>仅替换 Spring MVC 默认的 Jackson JSON 转换器，字符串、字节流等非 JSON
 * 转换器继续使用 Spring MVC 默认实现。Fastjson2 默认不输出值为 {@code null} 的字段。</p>
 */
@Configuration(proxyBeanMethods = false)
public class FastJsonConfiguration implements WebMvcConfigurer {

    /**
     * 替换 Spring MVC 默认的 Jackson JSON 转换器为 Fastjson2 实现。
     *
     * <p>仅替换 JSON 转换器，字符串、字节流等非 JSON 转换器继续使用默认实现。</p>
     */
    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.withJsonConverter(createFastJsonConverter());
    }

    /** 创建 Fastjson2 JSON 转换器，配置 UTF-8 编码、日期格式和支持的媒体类型。 */
    private static FastJsonHttpMessageConverter createFastJsonConverter() {
        FastJsonConfig config = new FastJsonConfig();
        config.setCharset(StandardCharsets.UTF_8);
        config.setDateFormat("yyyy-MM-dd HH:mm:ss");

        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
        converter.setFastJsonConfig(config);
        converter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                new MediaType("application", "*+json")
        ));
        return converter;
    }
}
