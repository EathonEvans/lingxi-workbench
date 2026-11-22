package com.lingxi.workbench.framework.web;

import com.google.gson.Gson;
import com.lingxi.workbench.framework.json.JSON;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 所有启动模块共用的 Gson 序列化配置。
 *
 * <p>Spring MVC 默认会优先选择 Jackson。项目中的 JSONB、缓存、消息和 HTTP
 * 响应必须使用同一套 Gson 规则，否则工作流节点类型、枚举编码和字段命名可能在
 * 不同边界产生不一致的快照。因此这里复用 framework.json 提供的 Gson 实例，统一
 * 应用层和 Web 层的序列化行为。</p>
 */
@Configuration(proxyBeanMethods = false)
public class GsonConfiguration implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.withJsonConverter(createGsonConverter());
    }

    /**
     * 创建只负责 JSON 的 HTTP 转换器，并保留 Spring MVC 对字符串、字节流等
     * 非 JSON 内容的默认处理能力。
     */
    private static GsonHttpMessageConverter createGsonConverter() {
        Gson gson = JSON.gson();
        GsonHttpMessageConverter converter = new GsonHttpMessageConverter(gson);
        converter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                new MediaType("application", "*+json")
        ));
        return converter;
    }
}
