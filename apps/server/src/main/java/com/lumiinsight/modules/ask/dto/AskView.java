package com.lumiinsight.modules.ask.dto;

import com.lumiinsight.modules.evidence.dto.EvidenceView;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AskView {
    private String answer;
    private String source;
    private List<EvidenceView> evidences;
}
