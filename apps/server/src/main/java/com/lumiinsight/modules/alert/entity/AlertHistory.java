package com.lumiinsight.modules.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert_history")
public class AlertHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String kind;
    private String aspect;
    private String recentLabel;
    private String previousLabel;
    private Integer recentNeg;
    private Integer previousNeg;
    private Integer recentVolume;
    private Integer previousVolume;
    private String summary;
    private String source;
    private String evidenceJson;
    private LocalDateTime createdAt;
}
