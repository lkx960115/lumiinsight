package com.lumiinsight.modules.llm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RouteSaveRequest {
    @NotBlank
    private String purposeCode;
    private Long primaryModelId;
    private Long backupModelId;
    private Integer enabled = 1;
}
