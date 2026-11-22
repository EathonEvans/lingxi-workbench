package com.lingxi.workbench.framework.storage.config;

import com.lingxi.workbench.framework.storage.ObjectStorage;
import com.lingxi.workbench.framework.storage.impl.AliOssObjectStorage;
import com.lingxi.workbench.framework.storage.impl.MinioObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** 统一对象存储实现选择测试。 */
class ObjectStorageAutoConfigurationTest {

    @Test
    void shouldNotCreateStorageWithoutType() {
        try (AnnotationConfigApplicationContext context = createContext(Map.of())) {
            assertEquals(0, context.getBeansOfType(ObjectStorage.class).size());
        }
    }

    @Test
    void shouldOnlyCreateMinioStorage() {
        try (AnnotationConfigApplicationContext context = createContext(Map.of(
                "lingxi.storage.type", "minio",
                "lingxi.storage.minio.endpoint", "http://localhost:9000",
                "lingxi.storage.minio.access-key", "lingxi",
                "lingxi.storage.minio.secret-key", "secret",
                "lingxi.storage.minio.bucket", "knowledge"))) {
            assertEquals(1, context.getBeansOfType(ObjectStorage.class).size());
            assertInstanceOf(MinioObjectStorage.class, context.getBean(ObjectStorage.class));
        }
    }

    @Test
    void shouldOnlyCreateOssStorage() {
        try (AnnotationConfigApplicationContext context = createContext(Map.of(
                "lingxi.storage.type", "oss",
                "lingxi.storage.oss.endpoint", "https://oss-cn-chengdu.aliyuncs.com",
                "lingxi.storage.oss.access-key-id", "test-key",
                "lingxi.storage.oss.access-key-secret", "test-secret",
                "lingxi.storage.oss.bucket", "test-bucket"))) {
            assertEquals(1, context.getBeansOfType(ObjectStorage.class).size());
            assertInstanceOf(AliOssObjectStorage.class, context.getBean(ObjectStorage.class));
        }
    }

    /** 使用指定配置创建轻量Spring上下文。 */
    private static AnnotationConfigApplicationContext createContext(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("test", properties));
        context.register(ObjectStorageAutoConfiguration.class);
        context.refresh();
        return context;
    }
}
