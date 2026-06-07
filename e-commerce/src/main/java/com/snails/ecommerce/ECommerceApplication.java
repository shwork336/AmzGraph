package com.snails.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
/**
 * 电商 AI 图文资产流水线后端应用入口。
 *
 * <p>当前工程按模块化单体组织，所有业务模块部署在同一个 Spring Boot 应用内。</p>
 */
public class ECommerceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ECommerceApplication.class, args);
    }
}
