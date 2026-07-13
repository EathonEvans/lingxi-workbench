package com.lingxi.workbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.lingxi.workbench")
public class LingxiWorkbenchApplication {

    public static void main(String[] args) {
        SpringApplication.run(LingxiWorkbenchApplication.class, args);
    }
}
