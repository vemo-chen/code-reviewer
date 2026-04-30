package com.vemo.codereview.report.entity;

import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;

@TableName("daily_report_record")
@Getter
@Setter
public class DailyReportRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Date reportDate;
    private Long projectId;
    private String developerId;
    private Integer commitCount;
    private Integer mrCount;
    private Integer reviewCount;
    private Integer highRiskCount;
    private String summary;
    private Date createdAt;

}
