package com.lingxi.workbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * User-facing Lingxi Workbench application.
 */
@SpringBootApplication(scanBasePackages = "com.lingxi.workbench")
public class LingxiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LingxiApplication.class, args);
    }
}
