package com.lumiinsight.modules.ask;

import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.ask.dto.AskRequest;
import com.lumiinsight.modules.ask.dto.AskView;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @PostMapping("/{projectId}/ask")
    @PreAuthorize("hasAnyAuthority('review:view', 'project:view', 'report:view')")
    public ApiResult<AskView> ask(@PathVariable Long projectId, @Valid @RequestBody AskRequest request) {
        return ApiResult.ok(askService.ask(projectId, request.getQuestion()));
    }
}
