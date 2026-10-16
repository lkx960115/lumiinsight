package com.lumiinsight.modules.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportCompareExportRow {
    @ExcelProperty("对象")
    private String name;
    @ExcelProperty("原评")
    private Long volume;
    @ExcelProperty("正向")
    private Long pos;
    @ExcelProperty("负向")
    private Long neg;
    @ExcelProperty("中性")
    private Long neu;
    @ExcelProperty("主要痛点")
    private String topPain;
}
