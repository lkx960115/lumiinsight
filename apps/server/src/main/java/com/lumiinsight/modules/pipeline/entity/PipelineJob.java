package com.lumiinsight.modules.pipeline.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pipeline_job")
public class PipelineJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String type;
    private String status;
    private String message;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
