package com.lumiinsight.modules.report.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportAspectCompareExportRow {
    @ExcelProperty("方面")
    private String aspect;
    @ExcelProperty("对象")
    private String name;
    @ExcelProperty("正向")
    private Long pos;
    @ExcelProperty("负向")
    private Long neg;
}
