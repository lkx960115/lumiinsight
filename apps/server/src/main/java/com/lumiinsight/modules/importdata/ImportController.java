package com.lumiinsight.modules.importdata;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.importdata.entity.ImportJob;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/projects/{projectId}/imports")
    @PreAuthorize("hasAuthority('import:execute')")
    public ApiResult<ImportJob> submit(@PathVariable Long projectId, @RequestParam("file") MultipartFile file) {
        return ApiResult.ok(importService.submit(projectId, file));
    }

    @GetMapping("/imports/{jobId}")
    @PreAuthorize("hasAuthority('import:execute')")
    public ApiResult<ImportJob> get(@PathVariable Long jobId) {
        return ApiResult.ok(importService.get(jobId));
    }

    @GetMapping("/imports/{jobId}/errors")
    @PreAuthorize("hasAuthority('import:execute')")
    public ResponseEntity<byte[]> errors(@PathVariable Long jobId) {
        byte[] bytes = importService.downloadErrors(jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=import-errors.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    @GetMapping("/admin/jobs")
    @PreAuthorize("hasAuthority('admin:job:view')")
    public ApiResult<Page<ImportJob>> adminJobs(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResult.ok(importService.page(projectId, page, size));
    }

    @GetMapping("/projects/{projectId}/imports")
    @PreAuthorize("hasAuthority('import:execute')")
    public ApiResult<Page<ImportJob>> projectJobs(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResult.ok(importService.page(projectId, page, size));
    }
}
