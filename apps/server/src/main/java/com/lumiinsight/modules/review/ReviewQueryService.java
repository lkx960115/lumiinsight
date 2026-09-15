package com.lumiinsight.modules.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.util.MaskingUtil;
import com.lumiinsight.modules.project.ProjectService;
import com.lumiinsight.modules.review.dto.ReviewAspectView;
import com.lumiinsight.modules.review.dto.ReviewView;
import com.lumiinsight.modules.review.entity.Review;
import com.lumiinsight.modules.review.entity.ReviewAspect;
import com.lumiinsight.modules.review.mapper.ReviewAspectMapper;
import com.lumiinsight.modules.review.mapper.ReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewQueryService {

    private final ReviewMapper reviewMapper;
    private final ReviewAspectMapper reviewAspectMapper;
    private final ProjectService projectService;

    public ReviewQueryService(
            ReviewMapper reviewMapper,
            ReviewAspectMapper reviewAspectMapper,
            ProjectService projectService
    ) {
        this.reviewMapper = reviewMapper;
        this.reviewAspectMapper = reviewAspectMapper;
        this.projectService = projectService;
    }

    public Page<ReviewView> page(Long projectId, long page, long size, String platform, String keyword) {
        projectService.requireVisible(projectId);
        LambdaQueryWrapper<Review> q = new LambdaQueryWrapper<Review>()
                .eq(Review::getProjectId, projectId)
                .eq(StringUtils.hasText(platform), Review::getPlatform, platform)
                .like(StringUtils.hasText(keyword), Review::getContent, keyword)
                .orderByDesc(Review::getId);
        Page<Review> raw = reviewMapper.selectPage(new Page<>(page, size), q);
        List<ReviewView> views = raw.getRecords().stream().map(this::toView).toList();
        fillAspects(views);
        Page<ReviewView> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(views);
        return out;
    }

    private void fillAspects(List<ReviewView> views) {
        if (views.isEmpty()) {
            return;
        }
        List<Long> ids = views.stream().map(ReviewView::getId).toList();
        List<ReviewAspect> rows = reviewAspectMapper.selectList(
                new LambdaQueryWrapper<ReviewAspect>().in(ReviewAspect::getReviewId, ids)
        );
        Map<Long, List<ReviewAspectView>> grouped = new HashMap<>();
        for (ReviewAspect row : rows) {
            grouped.computeIfAbsent(row.getReviewId(), key -> new ArrayList<>()).add(
                    ReviewAspectView.builder()
                            .name(row.getAspectName())
                            .sentiment(row.getSentiment())
                            .reason(row.getReason())
                            .confidence(row.getConfidence())
                            .source(row.getSource())
                            .build()
            );
        }
        for (ReviewView view : views) {
            view.setAspects(grouped.getOrDefault(view.getId(), List.of()));
        }
    }

    private ReviewView toView(Review r) {
        return ReviewView.builder()
                .id(r.getId())
                .platform(r.getPlatform())
                .productId(r.getProductId())
                .reviewId(r.getReviewId())
                .content(MaskingUtil.maskReview(r.getContent()))
                .reviewTime(r.getReviewTime())
                .likeCount(r.getLikeCount())
                .productName(r.getProductName())
                .sourceUrl(r.getSourceUrl())
                .cleanTags(r.getCleanTags())
                .cleanReason(r.getCleanReason())
                .aspectHits(r.getAspectHits())
                .counted(r.getCounted())
                .sentiment(r.getSentiment())
                .sentimentReason(r.getSentimentReason())
                .sentimentConfidence(r.getSentimentConfidence())
                .analyzeSource(r.getAnalyzeSource())
                .build();
    }
}
