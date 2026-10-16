package com.lumiinsight.modules.review;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.evidence.SemanticReviewService;
import com.lumiinsight.modules.review.dto.ProjectOverview;
import com.lumiinsight.modules.review.dto.ReviewView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ReviewController {

    private final ReviewQueryService reviewQueryService;
    private final SemanticReviewService semanticReviewService;

    public ReviewController(ReviewQueryService reviewQueryService, SemanticReviewService semanticReviewService) {
        this.reviewQueryService = reviewQueryService;
        this.semanticReviewService = semanticReviewService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyAuthority('review:view', 'project:view')")
    public ApiResult<ProjectOverview> overview(@PathVariable Long projectId) {
        return ApiResult.ok(reviewQueryService.overview(projectId));
    }

    @GetMapping("/reviews")
    @PreAuthorize("hasAuthority('review:view')")
    public ApiResult<Page<ReviewView>> page(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer counted,
            @RequestParam(required = false) String aspect
    ) {
        return ApiResult.ok(reviewQueryService.page(projectId, page, size, platform, keyword, counted, aspect));
    }

    @GetMapping("/reviews/semantic")
    @PreAuthorize("hasAuthority('review:view')")
    public ApiResult<Page<ReviewView>> semantic(
            @PathVariable Long projectId,
            @RequestParam String q,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String aspect
    ) {
        return ApiResult.ok(semanticReviewService.search(projectId, q, platform, aspect));
    }
}
