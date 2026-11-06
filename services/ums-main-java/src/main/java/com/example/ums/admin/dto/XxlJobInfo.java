package com.example.ums.admin.dto;

import lombok.Data;

/**
 * XXL-JOB 定时任务视图（对应 xxl_job_info，管理后台维护列表用）。
 * 时间字段为毫秒时间戳（0 表示从未触发/未排程）。
 */
@Data
public class XxlJobInfo {

    private Integer id;

    /** 执行器分组 ID */
    private Integer jobGroup;

    /** 任务描述 */
    private String jobDesc;

    /** 调度类型（CRON / FIX_RATE） */
    private String scheduleType;

    /** 调度配置（cron 表达式或固定速率秒数） */
    private String scheduleConf;

    /** 执行器路由策略 */
    private String executorRouteStrategy;

    /** JobHandler 名称 */
    private String executorHandler;

    /** 任务参数 */
    private String executorParam;

    /** 调度过期策略 */
    private String misfireStrategy;

    /** 阻塞处理策略 */
    private String executorBlockStrategy;

    /** 负责人 */
    private String author;

    /** 报警邮件 */
    private String alarmEmail;

    /** 调度状态：0 停止 / 1 运行 */
    private Integer triggerStatus;

    /** 上次触发时间（毫秒时间戳） */
    private Long triggerLastTime;

    /** 下次触发时间（毫秒时间戳） */
    private Long triggerNextTime;

    private Long createTime;

    private Long updateTime;
}
