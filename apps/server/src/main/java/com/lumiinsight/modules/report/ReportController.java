package com.lumiinsight.modules.report;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.report.dto.ReportView;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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

    @GetMapping("/reports/{id}/export")
    @PreAuthorize("hasAuthority('report:view')")
    public ResponseEntity<byte[]> export(@PathVariable Long id, @RequestParam String format) {
        ReportExporter.File file = reportService.export(id, format);
        String encoded = URLEncoder.encode(file.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(file.type())
                .body(file.bytes());
    }
}
