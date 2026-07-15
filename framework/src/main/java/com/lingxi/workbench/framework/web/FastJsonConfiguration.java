package com.lingxi.workbench.framework.web;

import com.alibaba.fastjson2.support.config.FastJsonConfig;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        int jsonConverterIndex = findJsonConverterIndex(converters);
        converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
        converters.add(Math.min(jsonConverterIndex, converters.size()), createFastJsonConverter());
    }

    private static int findJsonConverterIndex(List<HttpMessageConverter<?>> converters) {
        for (int index = 0; index < converters.size(); index++) {
            if (converters.get(index) instanceof MappingJackson2HttpMessageConverter) {
                return index;
            }
        }
        return converters.size();
    }

    private static FastJsonHttpMessageConverter createFastJsonConverter() {
        FastJsonConfig config = new FastJsonConfig();
        config.setCharset(StandardCharsets.UTF_8);
        config.setDateFormat("yyyy-MM-dd HH:mm:ss");

        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
        converter.setFastJsonConfig(config);
        return converter;
    }
}
