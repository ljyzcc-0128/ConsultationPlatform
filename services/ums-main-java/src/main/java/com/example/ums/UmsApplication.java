package com.example.ums;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 储能资讯平台一期 Java 主服务（模块化单体，ADR-01）。
 * 包结构对应四层架构，详见《Java后端实现指南 v1.0》第一节。
 */
@SpringBootApplication
@MapperScan({
    "com.example.ums.processing.mapper",
    "com.example.ums.ai.mapper",
    "com.example.ums.admin.mapper",
    "com.example.ums.user.mapper",
    "com.example.ums.subscription.mapper",
    "com.example.ums.feed.mapper",
    "com.example.ums.push.mapper",
    "com.example.ums.supplychain.mapper"
})
@ConfigurationPropertiesScan("com.example.ums")
public class UmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(UmsApplication.class, args);
    }
}
