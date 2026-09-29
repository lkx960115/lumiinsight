package com.lumiinsight.modules.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserSaveRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    private String password;
    @NotBlank(message = "显示名不能为空")
    private String displayName;
    @NotBlank(message = "角色不能为空")
    private String roleCode;
    @NotNull
    private Integer enabled;
}
