package com.lumiinsight.modules.review.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewView {
    private Long id;
    private String platform;
    private String productId;
    private String reviewId;
    private String content;
    private LocalDateTime reviewTime;
    private Integer likeCount;
    private String productName;
    private String sourceUrl;
    private String cleanTags;
    private Integer counted;
}
