package com.lingxi.workbench.config;

import com.lingxi.workbench.framework.storage.ObjectStorage;
import com.lingxi.workbench.framework.storage.impl.MinioObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Application模块对象存储依赖与配置装配测试。 */
class ApplicationObjectStorageConfigurationTest {

    @Test
    void shouldCreateMinioStorageBeanFromApplicationClasspath() {
        Map<String, Object> properties = Map.of(
                "lingxi.storage.type", "minio",
                "lingxi.storage.minio.endpoint", "http://localhost:9000",
                "lingxi.storage.minio.access-key", "lingxi",
                "lingxi.storage.minio.secret-key", "secret",
                "lingxi.storage.minio.bucket", "knowledge");

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("test", properties));
            context.scan("com.lingxi.workbench.framework.storage.config");
            context.refresh();

            assertEquals(1, context.getBeansOfType(ObjectStorage.class).size());
            assertInstanceOf(MinioObjectStorage.class, context.getBean(ObjectStorage.class));
        }
    }
}
