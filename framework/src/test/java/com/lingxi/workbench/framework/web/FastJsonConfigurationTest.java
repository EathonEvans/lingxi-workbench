package com.lingxi.workbench.framework.web;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FastJsonConfigurationTest {

    @Test
    void shouldReplaceJacksonConverterAndKeepOtherConverters() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        converters.add(new StringHttpMessageConverter());
        converters.add(new MappingJackson2HttpMessageConverter());

        new FastJsonConfiguration().extendMessageConverters(converters);

        assertInstanceOf(StringHttpMessageConverter.class, converters.getFirst());
        assertInstanceOf(FastJsonHttpMessageConverter.class, converters.get(1));
        assertFalse(converters.stream().anyMatch(MappingJackson2HttpMessageConverter.class::isInstance));
    }

    @Test
    void shouldNotWriteNullProperties() {
        assertEquals("{\"name\":\"灵析\"}", JSON.toJSONString(new JsonSample("灵析", null)));
    }

    private record JsonSample(String name, String description) {
    }
}
