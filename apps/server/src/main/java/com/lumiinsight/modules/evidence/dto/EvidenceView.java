package com.lumiinsight.modules.evidence.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EvidenceView {
    private Long id;
    private Long reviewId;
    private String aspectName;
    private String sentiment;
    private String quote;
    private String platform;
    private String content;
    private LocalDateTime reviewTime;
}
