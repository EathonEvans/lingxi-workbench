package com.lingxi.workbench.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/** MCP 协议服务当前不直接访问数据库，业务能力通过 application 稳定接口接入。 */
@SpringBootApplication(
        scanBasePackages = "com.lingxi.workbench",
        exclude = DataSourceAutoConfiguration.class)
public class LingxiMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LingxiMcpServerApplication.class, args);
    }
}
