package com.example.ums.admin.dto;

import lombok.Data;

import java.util.List;

/** XXL-JOB 任务分页结果。 */
@Data
public class XxlJobPage {

    private long total;

    private List<XxlJobInfo> list;
}
