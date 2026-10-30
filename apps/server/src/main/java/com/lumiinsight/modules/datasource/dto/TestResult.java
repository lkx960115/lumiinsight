package com.lumiinsight.modules.datasource.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class TestResult {
    private boolean ok;
    private Integer code;
    private String message;
    private int httpStatus;
    private int itemCount;
    private BigDecimal costYuan;
    private List<FieldProbe> fields;
    private String rawSample;

    @Data
    @Builder
    public static class FieldProbe {
        private String name;
        private boolean ok;
        private String sample;
    }
}
