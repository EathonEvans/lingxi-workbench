package com.lingxi.workbench.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.lingxi.workbench")
public class LingxiMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LingxiMcpServerApplication.class, args);
    }
}
