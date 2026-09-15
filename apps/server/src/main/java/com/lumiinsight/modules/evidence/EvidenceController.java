package com.lumiinsight.modules.evidence;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.evidence.dto.EvidenceView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/evidence")
public class EvidenceController {

    private final EvidenceService evidenceService;

    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('review:view')")
    public ApiResult<Page<EvidenceView>> page(
            @PathVariable Long projectId,
            @RequestParam(required = false) String aspect,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size
    ) {
        return ApiResult.ok(evidenceService.page(projectId, aspect, page, size));
    }
}
