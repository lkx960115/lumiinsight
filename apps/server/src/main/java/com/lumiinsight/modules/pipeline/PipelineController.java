package com.lumiinsight.modules.pipeline;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.pipeline.entity.PipelineJob;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PipelineController {

    private final PipelineService pipelineService;

    public PipelineController(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @PostMapping("/projects/{projectId}/pipeline/clean")
    @PreAuthorize("hasAuthority('pipeline:execute')")
    public ApiResult<PipelineJob> clean(@PathVariable Long projectId) {
        return ApiResult.ok(pipelineService.triggerClean(projectId));
    }

    @GetMapping("/pipeline/{jobId}")
    @PreAuthorize("hasAuthority('pipeline:execute')")
    public ApiResult<PipelineJob> get(@PathVariable Long jobId) {
        return ApiResult.ok(pipelineService.get(jobId));
    }

    @GetMapping("/projects/{projectId}/pipeline")
    @PreAuthorize("hasAuthority('pipeline:execute')")
    public ApiResult<Page<PipelineJob>> page(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResult.ok(pipelineService.page(projectId, page, size));
    }
}
