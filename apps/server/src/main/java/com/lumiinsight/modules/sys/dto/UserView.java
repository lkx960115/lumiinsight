package com.lumiinsight.modules.sys.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserView {
    private Long id;
    private String username;
    private String displayName;
    private Integer enabled;
    private String roleCode;
    private String roleName;
}
