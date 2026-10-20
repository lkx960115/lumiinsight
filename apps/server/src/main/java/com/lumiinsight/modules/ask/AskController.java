package com.lumiinsight.modules.ask;

import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.ask.dto.AskRequest;
import com.lumiinsight.modules.ask.dto.AskView;
import com.lumiinsight.modules.ask.dto.AskTurnView;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class AskController {

    private final AskService askService;

    public AskController(AskService askService) {
        this.askService = askService;
    }

    @GetMapping("/{projectId}/ask")
    @PreAuthorize("hasAnyAuthority('review:view', 'project:view', 'report:view')")
    public ApiResult<List<AskTurnView>> history(@PathVariable Long projectId) {
        return ApiResult.ok(askService.history(projectId));
    }

    @PostMapping("/{projectId}/ask")
    @PreAuthorize("hasAnyAuthority('review:view', 'project:view', 'report:view')")
    public ApiResult<AskView> ask(@PathVariable Long projectId, @Valid @RequestBody AskRequest request) {
        return ApiResult.ok(askService.ask(projectId, request.getQuestion()));
    }
}
