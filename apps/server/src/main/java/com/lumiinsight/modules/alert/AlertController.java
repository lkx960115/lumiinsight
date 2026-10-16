package com.lumiinsight.modules.alert;

import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.alert.dto.AlertView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/{projectId}/alerts")
    @PreAuthorize("hasAnyAuthority('review:view', 'project:view')")
    public ApiResult<List<AlertView>> list(@PathVariable Long projectId) {
        return ApiResult.ok(alertService.list(projectId));
    }
}
