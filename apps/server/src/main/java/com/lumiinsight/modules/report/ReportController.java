package com.lumiinsight.modules.report;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.report.dto.ReportView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('report:view')")
    public ApiResult<Page<ReportView>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResult.ok(reportService.page(page, size));
    }

    @GetMapping("/reports/{id}")
    @PreAuthorize("hasAuthority('report:view')")
    public ApiResult<ReportView> detail(@PathVariable Long id) {
        return ApiResult.ok(reportService.detail(id));
    }

    @GetMapping("/projects/{projectId}/reports/latest")
    @PreAuthorize("hasAuthority('report:view')")
    public ApiResult<ReportView> latest(@PathVariable Long projectId) {
        return ApiResult.ok(reportService.latest(projectId));
    }

    @PostMapping("/projects/{projectId}/reports")
    @PreAuthorize("hasAuthority('report:generate')")
    public ApiResult<ReportView> generate(@PathVariable Long projectId) {
        return ApiResult.ok(reportService.generateForProject(projectId));
    }
}
