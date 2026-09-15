package com.lumiinsight.modules.review.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProjectOverview {
    private long imported;
    private long counted;
    private long analyzed;
    private long pos;
    private long neg;
    private long neu;
    private List<AspectItem> aspects;

    @Data
    @Builder
    public static class AspectItem {
        private String name;
        private long total;
        private long pos;
        private long neg;
        private long neu;
    }
}
