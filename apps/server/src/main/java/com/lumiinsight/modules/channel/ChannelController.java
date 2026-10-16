package com.lumiinsight.modules.channel;

import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.project.ProjectService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/channels")
public class ChannelController {

    private final ChannelAdapterRegistry registry;
    private final ProjectService projectService;

    public ChannelController(ChannelAdapterRegistry registry, ProjectService projectService) {
        this.registry = registry;
        this.projectService = projectService;
    }

    @PostMapping("/pull")
    @PreAuthorize("hasAuthority('import:execute')")
    public ApiResult<Void> pull(@PathVariable Long projectId, @RequestParam String platform) {
        projectService.requireVisible(projectId);
        registry.pull(projectId, platform);
        return ApiResult.ok();
    }
}
