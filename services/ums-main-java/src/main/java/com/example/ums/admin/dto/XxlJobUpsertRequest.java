package com.example.ums.admin.dto;

import lombok.Data;

/**
 * XXL-JOB 任务新增/编辑请求体。
 * 未填字段使用调度中心常用默认值（CRON / 不触发补偿 / 第一个 / 单机串行 / BEAN）。
 */
@Data
public class XxlJobUpsertRequest {

    /** 任务描述（必填） */
    private String jobDesc;

    /** 调度类型：CRON / FIX_RATE，默认 CRON */
    private String scheduleType = "CRON";

    /** 调度配置：cron 表达式或固定速率秒数（必填） */
    private String scheduleConf;

    /** JobHandler 名称（必填） */
    private String executorHandler;

    /** 任务参数 */
    private String executorParam = "";

    /** 负责人 */
    private String author;

    /** 报警邮件 */
    private String alarmEmail = "";

    /** 调度过期策略：DO_NOTHING / FIRE_ONCE_NOW，默认 DO_NOTHING */
    private String misfireStrategy = "DO_NOTHING";

    /** 路由策略：FIRST / ROUND / RANDOM / ...，默认 FIRST */
    private String executorRouteStrategy = "FIRST";

    /** 阻塞处理策略：SERIAL_EXECUTION / DISCARD_LATER / COVER_EARLY */
    private String executorBlockStrategy = "SERIAL_EXECUTION";
}
