package com.lingxi.workbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 灵犀工作台应用端启动入口。
 */
@SpringBootApplication(scanBasePackages = "com.lingxi.workbench")
public class LingxiApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(LingxiApplication.class, args);
    }
}
