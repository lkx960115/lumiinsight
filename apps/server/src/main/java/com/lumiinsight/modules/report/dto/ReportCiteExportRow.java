package com.lumiinsight.modules.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportCiteExportRow {
    @ExcelProperty("编号")
    private Integer no;
    @ExcelProperty("平台")
    private String platform;
    @ExcelProperty("时间")
    private String reviewTime;
    @ExcelProperty("方面")
    private String aspect;
    @ExcelProperty("情感")
    private String sentiment;
    @ExcelProperty("原文")
    private String content;
}
