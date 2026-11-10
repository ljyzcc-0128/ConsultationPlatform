package com.example.ums.supplychain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 指标时序数据点（cp_indicator_point）。 */
@TableName("cp_indicator_point")
public class CpIndicatorPoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String seriesCode;

    private LocalDate pointDate;

    /** 明细主体（同指标多市场，如卓创华东各省市行）；无细分留空串 */
    private String subject;

    private BigDecimal value;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSeriesCode() { return seriesCode; }
    public void setSeriesCode(String seriesCode) { this.seriesCode = seriesCode; }
    public LocalDate getPointDate() { return pointDate; }
    public void setPointDate(LocalDate pointDate) { this.pointDate = pointDate; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
}
