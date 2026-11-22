package com.lingxi.workbench.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Lingxi Workbench management application.
 */
@SpringBootApplication(scanBasePackages = "com.lingxi.workbench")
public class LingxiAdminApplication {

    /**
     * 管理端服务启动入口。
     */
    public static void main(String[] args) {
        SpringApplication.run(LingxiAdminApplication.class, args);
    }
}
