package com.lumiinsight.modules.evidence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.util.MaskingUtil;
import com.lumiinsight.modules.evidence.dto.EvidenceView;
import com.lumiinsight.modules.evidence.entity.Evidence;
import com.lumiinsight.modules.evidence.mapper.EvidenceMapper;
import com.lumiinsight.modules.project.ProjectService;
import com.lumiinsight.modules.review.entity.Review;
import com.lumiinsight.modules.review.entity.ReviewAspect;
import com.lumiinsight.modules.review.mapper.ReviewAspectMapper;
import com.lumiinsight.modules.review.mapper.ReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EvidenceService {

    private final EvidenceMapper evidenceMapper;
    private final ReviewAspectMapper reviewAspectMapper;
    private final ReviewMapper reviewMapper;
    private final ProjectService projectService;

    public EvidenceService(
            EvidenceMapper evidenceMapper,
            ReviewAspectMapper reviewAspectMapper,
            ReviewMapper reviewMapper,
            ProjectService projectService
    ) {
        this.evidenceMapper = evidenceMapper;
        this.reviewAspectMapper = reviewAspectMapper;
        this.reviewMapper = reviewMapper;
        this.projectService = projectService;
    }

    public void replaceForReviews(Long projectId, List<Long> reviewIds) {
        if (projectId == null || reviewIds == null || reviewIds.isEmpty()) {
            return;
        }
        evidenceMapper.delete(
                new LambdaQueryWrapper<Evidence>()
                        .eq(Evidence::getProjectId, projectId)
                        .in(Evidence::getReviewId, reviewIds)
        );
        List<ReviewAspect> rows = reviewAspectMapper.selectList(
                new LambdaQueryWrapper<ReviewAspect>()
                        .eq(ReviewAspect::getProjectId, projectId)
                        .in(ReviewAspect::getReviewId, reviewIds)
        );
        Map<Long, Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>().in(Review::getId, reviewIds)
        ).stream().collect(Collectors.toMap(Review::getId, Function.identity(), (a, b) -> a));
        for (ReviewAspect row : rows) {
            if (!StringUtils.hasText(row.getAspectName())) {
                continue;
            }
            Evidence evidence = new Evidence();
            evidence.setProjectId(projectId);
            evidence.setReviewId(row.getReviewId());
            evidence.setAspectName(row.getAspectName());
            evidence.setSentiment(row.getSentiment() == null ? "neu" : row.getSentiment());
            Review review = reviews.get(row.getReviewId());
            evidence.setQuote(quoteOf(row.getReason(), review == null ? null : review.getContent()));
            evidence.setSource(row.getSource() == null ? "rule" : row.getSource());
            evidenceMapper.insert(evidence);
        }
    }

    public Page<EvidenceView> page(Long projectId, String aspect, long page, long size) {
        projectService.requireVisible(projectId);
        LambdaQueryWrapper<Evidence> q = new LambdaQueryWrapper<Evidence>()
                .eq(Evidence::getProjectId, projectId)
                .eq(StringUtils.hasText(aspect), Evidence::getAspectName, aspect)
                .orderByDesc(Evidence::getId);
        Page<Evidence> raw = evidenceMapper.selectPage(new Page<>(page, size), q);
        List<Long> reviewIds = raw.getRecords().stream().map(Evidence::getReviewId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Review> reviews = reviewIds.isEmpty()
                ? Map.of()
                : reviewMapper.selectList(new LambdaQueryWrapper<Review>().in(Review::getId, reviewIds)).stream()
                .collect(Collectors.toMap(Review::getId, Function.identity(), (a, b) -> a));
        List<EvidenceView> views = new ArrayList<>();
        for (Evidence row : raw.getRecords()) {
            Review review = reviews.get(row.getReviewId());
            views.add(EvidenceView.builder()
                    .id(row.getId())
                    .reviewId(row.getReviewId())
                    .aspectName(row.getAspectName())
                    .sentiment(row.getSentiment())
                    .quote(row.getQuote())
                    .platform(review == null ? null : review.getPlatform())
                    .content(review == null ? null : MaskingUtil.maskReview(review.getContent()))
                    .reviewTime(review == null ? null : review.getReviewTime())
                    .build());
        }
        Page<EvidenceView> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(views);
        return out;
    }

    private static String quoteOf(String reason, String content) {
        if (StringUtils.hasText(reason)) {
            return trim(reason, 120);
        }
        return trim(content, 120);
    }

    private static String trim(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String value = text.trim();
        return value.length() <= max ? value : value.substring(0, max);
    }
}
