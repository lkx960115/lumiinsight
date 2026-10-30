package com.lumiinsight.modules.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportMonthCompareExportRow {
    @ExcelProperty("月份")
    private String month;
    @ExcelProperty("对象")
    private String name;
    @ExcelProperty("原评")
    private Long volume;
    @ExcelProperty("正向")
    private Long pos;
    @ExcelProperty("负向")
    private Long neg;
}
