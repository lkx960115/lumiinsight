package com.lumiinsight.modules.ask.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AskRequest {
    @NotBlank(message = "请输入要问的问题")
    private String question;
}
