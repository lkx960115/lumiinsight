package com.lumiinsight.modules.llm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ModelSaveRequest {
    @NotNull
    private Long providerId;
    @NotBlank
    private String modelCode;
    private Integer contextLength;
    private Integer jsonMode = 1;
    private BigDecimal inputPrice;
    private BigDecimal outputPrice;
    private Integer enabled = 1;
}
