package com.lumiinsight.modules.review.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ReviewAspectView {
    private String name;
    private String sentiment;
    private String reason;
    private BigDecimal confidence;
    private String source;
}
