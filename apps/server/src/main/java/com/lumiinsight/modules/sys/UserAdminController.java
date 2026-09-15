package com.lumiinsight.modules.sys;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.sys.dto.UserSaveRequest;
import com.lumiinsight.modules.sys.dto.UserView;
import com.lumiinsight.modules.sys.entity.SysRole;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('admin:user:view')")
    public ApiResult<Page<UserView>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResult.ok(userAdminService.page(page, size, keyword));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('admin:user:view')")
    public ApiResult<List<SysRole>> roles() {
        return ApiResult.ok(userAdminService.roles());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('admin:user:edit')")
    public ApiResult<Void> create(@Valid @RequestBody UserSaveRequest request) {
        userAdminService.create(request);
        return ApiResult.ok();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('admin:user:edit')")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        userAdminService.update(id, request);
        return ApiResult.ok();
    }
}
