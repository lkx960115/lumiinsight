package com.lumiinsight.modules.report.dto;

import com.lumiinsight.modules.evidence.dto.EvidenceView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ReportView {
    private Long id;
    private Long projectId;
    private String projectName;
    private String brand;
    private String mainModel;
    private String title;
    private String status;
    private String message;
    private LocalDateTime createdAt;
    private Snapshot snapshot;
    private List<Section> sections;
    private Map<Long, EvidenceView> evidences;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Snapshot {
        private long imported;
        private long counted;
        private long analyzed;
        private long pos;
        private long neg;
        private long neu;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        private String code;
        private String title;
        private List<Claim> claims;
        private List<CompareRow> compare;
        private List<AspectCompareRow> aspectCompare;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompareRow {
        private String name;
        private long volume;
        private long pos;
        private long neg;
        private long neu;
        private String topPain;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AspectCompareRow {
        private String aspect;
        private List<AspectScore> scores;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AspectScore {
        private String name;
        private long pos;
        private long neg;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Claim {
        private String text;
        private List<Long> evidenceIds;
    }
}
