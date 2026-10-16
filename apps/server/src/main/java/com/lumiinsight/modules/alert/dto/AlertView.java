package com.lumiinsight.modules.alert.dto;

import com.lumiinsight.modules.evidence.dto.EvidenceView;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AlertView {
    private String aspect;
    private String recentLabel;
    private String previousLabel;
    private int recentNeg;
    private int previousNeg;
    private String summary;
    private String source;
    private List<EvidenceView> evidences;
}
