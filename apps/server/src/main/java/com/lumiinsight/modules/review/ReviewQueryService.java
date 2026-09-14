package com.lumiinsight.modules.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.util.MaskingUtil;
import com.lumiinsight.modules.project.ProjectService;
import com.lumiinsight.modules.review.dto.ReviewView;
import com.lumiinsight.modules.review.entity.Review;
import com.lumiinsight.modules.review.mapper.ReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReviewQueryService {

    private final ReviewMapper reviewMapper;
    private final ProjectService projectService;

    public ReviewQueryService(ReviewMapper reviewMapper, ProjectService projectService) {
        this.reviewMapper = reviewMapper;
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
        Page<ReviewView> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(raw.getRecords().stream().map(this::toView).toList());
        return out;
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
                .build();
    }
}
