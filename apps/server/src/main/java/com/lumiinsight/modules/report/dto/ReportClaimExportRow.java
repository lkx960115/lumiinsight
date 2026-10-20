package com.lumiinsight.modules.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportClaimExportRow {
    @ExcelProperty("章节")
    private String section;
    @ExcelProperty("结论")
    private String text;
    @ExcelProperty("引用编号")
    private String citations;
}
