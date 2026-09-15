package com.lumiinsight.modules.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.util.MaskingUtil;
import com.lumiinsight.modules.project.ProjectService;
import com.lumiinsight.modules.review.dto.ProjectOverview;
import com.lumiinsight.modules.review.dto.ReviewAspectView;
import com.lumiinsight.modules.review.dto.ReviewView;
import com.lumiinsight.modules.review.entity.Review;
import com.lumiinsight.modules.review.entity.ReviewAspect;
import com.lumiinsight.modules.review.mapper.ReviewAspectMapper;
import com.lumiinsight.modules.review.mapper.ReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public ProjectOverview overview(Long projectId) {
        projectService.requireVisible(projectId);
        long imported = reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>().eq(Review::getProjectId, projectId)
        );
        List<Review> counted = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getProjectId, projectId)
                        .eq(Review::getCounted, 1)
        );
        long pos = 0;
        long neg = 0;
        long neu = 0;
        long analyzed = 0;
        Set<Long> countedIds = new HashSet<>();
        for (Review review : counted) {
            countedIds.add(review.getId());
            if (!StringUtils.hasText(review.getSentiment())) {
                continue;
            }
            analyzed++;
            if ("pos".equals(review.getSentiment())) {
                pos++;
            } else if ("neg".equals(review.getSentiment())) {
                neg++;
            } else {
                neu++;
            }
        }
        Map<String, long[]> grouped = new HashMap<>();
        if (!countedIds.isEmpty()) {
            List<ReviewAspect> rows = reviewAspectMapper.selectList(
                    new LambdaQueryWrapper<ReviewAspect>().eq(ReviewAspect::getProjectId, projectId)
            );
            for (ReviewAspect row : rows) {
                if (row.getReviewId() == null || !countedIds.contains(row.getReviewId()) || !StringUtils.hasText(row.getAspectName())) {
                    continue;
                }
                long[] counts = grouped.computeIfAbsent(row.getAspectName(), key -> new long[3]);
                if ("pos".equals(row.getSentiment())) {
                    counts[0]++;
                } else if ("neg".equals(row.getSentiment())) {
                    counts[1]++;
                } else {
                    counts[2]++;
                }
            }
        }
        List<ProjectOverview.AspectItem> aspects = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : grouped.entrySet()) {
            long[] counts = entry.getValue();
            aspects.add(ProjectOverview.AspectItem.builder()
                    .name(entry.getKey())
                    .pos(counts[0])
                    .neg(counts[1])
                    .neu(counts[2])
                    .total(counts[0] + counts[1] + counts[2])
                    .build());
        }
        aspects.sort(Comparator.comparingLong(ProjectOverview.AspectItem::getTotal).reversed());
        return ProjectOverview.builder()
                .imported(imported)
                .counted(counted.size())
                .analyzed(analyzed)
                .pos(pos)
                .neg(neg)
                .neu(neu)
                .aspects(aspects)
                .build();
    }

    public Page<ReviewView> page(Long projectId, long page, long size, String platform, String keyword, Integer counted) {
        projectService.requireVisible(projectId);
        LambdaQueryWrapper<Review> q = new LambdaQueryWrapper<Review>()
                .eq(Review::getProjectId, projectId)
                .eq(StringUtils.hasText(platform), Review::getPlatform, platform)
                .like(StringUtils.hasText(keyword), Review::getContent, keyword)
                .eq(counted != null, Review::getCounted, counted)
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
