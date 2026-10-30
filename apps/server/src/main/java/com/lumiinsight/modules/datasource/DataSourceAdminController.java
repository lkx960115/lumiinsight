package com.lumiinsight.modules.datasource;

import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.datasource.dto.ChannelSaveRequest;
import com.lumiinsight.modules.datasource.dto.ChannelView;
import com.lumiinsight.modules.datasource.dto.ProviderSaveRequest;
import com.lumiinsight.modules.datasource.dto.ProviderView;
import com.lumiinsight.modules.datasource.dto.TestResult;
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
@RequestMapping("/api/v1/admin/datasource")
public class DataSourceAdminController {

    private final DataSourceAdminService adminService;
    private final DataSourceTestService testService;

    public DataSourceAdminController(DataSourceAdminService adminService, DataSourceTestService testService) {
        this.adminService = adminService;
        this.testService = testService;
    }

    @GetMapping("/providers")
    @PreAuthorize("hasAuthority('admin:datasource:view')")
    public ApiResult<List<ProviderView>> providers() {
        return ApiResult.ok(adminService.providers());
    }

    @PostMapping("/providers")
    @PreAuthorize("hasAuthority('admin:datasource:edit')")
    public ApiResult<Void> createProvider(@Valid @RequestBody ProviderSaveRequest request) {
        adminService.saveProvider(null, request);
        return ApiResult.ok();
    }

    @PutMapping("/providers/{id}")
    @PreAuthorize("hasAuthority('admin:datasource:edit')")
    public ApiResult<Void> updateProvider(@PathVariable Long id, @Valid @RequestBody ProviderSaveRequest request) {
        adminService.saveProvider(id, request);
        return ApiResult.ok();
    }

    @GetMapping("/providers/{id}/channels")
    @PreAuthorize("hasAuthority('admin:datasource:view')")
    public ApiResult<List<ChannelView>> channels(@PathVariable Long id) {
        return ApiResult.ok(adminService.channels(id));
    }

    @PutMapping("/providers/{id}/channels")
    @PreAuthorize("hasAuthority('admin:datasource:edit')")
    public ApiResult<Void> saveChannel(@PathVariable Long id, @Valid @RequestBody ChannelSaveRequest request) {
        adminService.saveChannel(id, request);
        return ApiResult.ok();
    }

    /** 试拉第 1 页验证 Token 与字段映射，这次调用会计费 */
    @PostMapping("/providers/{id}/test")
    @PreAuthorize("hasAuthority('admin:datasource:edit')")
    public ApiResult<TestResult> test(
            @PathVariable Long id,
            @RequestParam String platform,
            @RequestParam String productId
    ) {
        return ApiResult.ok(testService.test(id, platform, productId));
    }
}
