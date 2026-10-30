package com.example.ums.scheduler;

import com.example.ums.push.jingcai.JingcaiProperties;
import com.example.ums.push.jingcai.JingcaiCardSender;
import com.example.ums.push.jingcai.SupplyChainDigestService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

/**
 * 晶彩卡片推送定时任务：供应链看板日报。
 *
 * <p>在 xxl-job 调度中心注册执行器 handler "jingcaiDigestJob"
 * （JobHandler 填 jingcaiDigestJob，建议每个工作日早上调度）。
 * 未启用晶彩推送（UMS_JINGCAI_ENABLED）时任务直接失败并给出原因，便于发现配置缺失。
 */
@Component
@EnableConfigurationProperties(JingcaiProperties.class)
@RequiredArgsConstructor
public class JingcaiDigestJob {

    private static final Logger log = LoggerFactory.getLogger(JingcaiDigestJob.class);

    private final SupplyChainDigestService digestService;
    private final JingcaiCardSender sender;
    private final JingcaiProperties jingcaiProperties;

    @XxlJob("jingcaiDigestJob")
    public void execute() {
        if (!jingcaiProperties.isEnabled()) {
            XxlJobHelper.handleFail("晶彩推送未启用（UMS_JINGCAI_ENABLED=false），跳过");
            return;
        }
        try {
            SupplyChainDigestService.DigestCard card = digestService.build();
            long taskId = sender.send(card.title(), card.markdown());
            XxlJobHelper.handleSuccess("已推送供应链日报卡片，钉钉 task_id=" + taskId);
        } catch (Exception e) {
            log.error("供应链日报卡片推送失败", e);
            XxlJobHelper.handleFail("推送失败：" + e.getMessage());
        }
    }
}
