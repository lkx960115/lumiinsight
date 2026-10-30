package com.lumiinsight.modules.ask.dto;

import com.lumiinsight.modules.evidence.dto.EvidenceView;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AskTurnView {
    private Long id;
    private String question;
    private String answer;
    private String source;
    private List<EvidenceView> evidences;
    private LocalDateTime createdAt;
}
