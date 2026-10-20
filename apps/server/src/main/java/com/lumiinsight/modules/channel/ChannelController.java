package com.lumiinsight.modules.channel;

import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.channel.dto.ChannelShopView;
import com.lumiinsight.modules.importdata.entity.ImportJob;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/channels")
public class ChannelController {

    private final ChannelSyncService channelSyncService;

    public ChannelController(ChannelSyncService channelSyncService) {
        this.channelSyncService = channelSyncService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('import:execute')")
    public ApiResult<List<ChannelShopView>> shops(@PathVariable Long projectId) {
        return ApiResult.ok(channelSyncService.shops(projectId));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('import:execute')")
    public ApiResult<ImportJob> sync(
            @PathVariable Long projectId,
            @RequestParam(required = false) String platform
    ) {
        return ApiResult.ok(channelSyncService.sync(projectId, platform));
    }

    @PostMapping("/pull")
    @PreAuthorize("hasAuthority('import:execute')")
    public ApiResult<ImportJob> pull(@PathVariable Long projectId, @RequestParam String platform) {
        return ApiResult.ok(channelSyncService.sync(projectId, platform));
    }
}
