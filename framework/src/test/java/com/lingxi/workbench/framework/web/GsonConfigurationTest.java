package com.lingxi.workbench.framework.web;

import com.lingxi.workbench.framework.json.JSON;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GsonConfigurationTest {

    @Test
    void shouldReplaceJacksonConverterAndKeepOtherConverters() {
        HttpMessageConverters.ServerBuilder builder = HttpMessageConverters.forServer().registerDefaults();

        new GsonConfiguration().configureMessageConverters(builder);
        List<HttpMessageConverter<?>> converters = StreamSupport.stream(builder.build().spliterator(), false).toList();

        assertTrue(converters.stream().anyMatch(StringHttpMessageConverter.class::isInstance));
        assertTrue(converters.stream().anyMatch(GsonHttpMessageConverter.class::isInstance));
        assertFalse(converters.stream().anyMatch(JacksonJsonHttpMessageConverter.class::isInstance));
    }

    @Test
    void shouldNotWriteNullProperties() {
        assertEquals("{\"name\":\"灵析\"}", JSON.toJSONString(new JsonSample("灵析", null)));
    }

    private record JsonSample(String name, String description) {
    }
}
