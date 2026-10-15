package com.example.ums.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-JOB 执行器配置（实现指南 ADR：单一调度系统 XXL-JOB）。
 * 本地默认关闭（调度中心不在时执行器会持续注册失败刷日志），
 * 生产/联调置 UMS_XXL_JOB_ENABLED=true。
 * 调度中心地址、accessToken 须与 xxl-job-admin 配置一致。
 */
@Configuration
@ConditionalOnProperty(name = "ums.xxl-job.enabled", havingValue = "true")
public class XxlJobConfig {

    private static final Logger log = LoggerFactory.getLogger(XxlJobConfig.class);

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor(
            @Value("${ums.xxl-job.admin-addresses}") String adminAddresses,
            @Value("${ums.xxl-job.appname}") String appname,
            @Value("${ums.xxl-job.port}") int port,
            @Value("${ums.xxl-job.access-token}") String accessToken) {
        log.info("XXL-JOB 执行器初始化：admin={}, appname={}, port={}", adminAddresses, appname, port);
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appname);
        executor.setPort(port);
        executor.setAccessToken(accessToken);
        return executor;
    }
}
