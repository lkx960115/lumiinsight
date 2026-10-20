package com.lumiinsight.modules.importdata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("import_job")
public class ImportJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String type;
    private String filename;
    private String objectKey;
    private String status;
    private Integer totalRows;
    private Integer successRows;
    private Integer failRows;
    private String errorObjectKey;
    private String message;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
