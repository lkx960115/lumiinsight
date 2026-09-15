package com.lumiinsight.modules.importdata;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ReviewImportRow {
    @ExcelProperty("平台")
    private String platform;
    @ExcelProperty("原文")
    private String content;
    @ExcelProperty("时间")
    private String reviewTime;
    @ExcelProperty("商品ID")
    private String productId;
    @ExcelProperty("评论ID")
    private String reviewId;
    @ExcelProperty("点赞")
    private String likeCount;
    @ExcelProperty("作者匿名ID")
    private String authorHash;
    @ExcelProperty("商品名")
    private String productName;
    @ExcelProperty("链接")
    private String sourceUrl;
    private int rowIndex;
    private String error;
}
