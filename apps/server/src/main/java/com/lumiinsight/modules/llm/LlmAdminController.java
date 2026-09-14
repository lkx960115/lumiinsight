package com.lumiinsight.modules.llm;

import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.llm.dto.ModelSaveRequest;
import com.lumiinsight.modules.llm.dto.ProviderSaveRequest;
import com.lumiinsight.modules.llm.dto.ProviderView;
import com.lumiinsight.modules.llm.dto.RouteSaveRequest;
import com.lumiinsight.modules.llm.entity.LlmModel;
import com.lumiinsight.modules.llm.entity.LlmRoute;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/llm")
public class LlmAdminController {

    private final LlmAdminService llmAdminService;

    public LlmAdminController(LlmAdminService llmAdminService) {
        this.llmAdminService = llmAdminService;
    }

    @GetMapping("/providers")
    @PreAuthorize("hasAuthority('admin:llm:view')")
    public ApiResult<List<ProviderView>> providers() {
        return ApiResult.ok(llmAdminService.providers());
    }

    @PostMapping("/providers")
    @PreAuthorize("hasAuthority('admin:llm:edit')")
    public ApiResult<Void> createProvider(@Valid @RequestBody ProviderSaveRequest request) {
        llmAdminService.saveProvider(null, request);
        return ApiResult.ok();
    }

    @PutMapping("/providers/{id}")
    @PreAuthorize("hasAuthority('admin:llm:edit')")
    public ApiResult<Void> updateProvider(@PathVariable Long id, @Valid @RequestBody ProviderSaveRequest request) {
        llmAdminService.saveProvider(id, request);
        return ApiResult.ok();
    }

    @GetMapping("/models")
    @PreAuthorize("hasAuthority('admin:llm:view')")
    public ApiResult<List<LlmModel>> models(@RequestParam(required = false) Long providerId) {
        return ApiResult.ok(llmAdminService.models(providerId));
    }

    @PostMapping("/models")
    @PreAuthorize("hasAuthority('admin:llm:edit')")
    public ApiResult<Void> createModel(@Valid @RequestBody ModelSaveRequest request) {
        llmAdminService.saveModel(null, request);
        return ApiResult.ok();
    }

    @PutMapping("/models/{id}")
    @PreAuthorize("hasAuthority('admin:llm:edit')")
    public ApiResult<Void> updateModel(@PathVariable Long id, @Valid @RequestBody ModelSaveRequest request) {
        llmAdminService.saveModel(id, request);
        return ApiResult.ok();
    }

    @GetMapping("/routes")
    @PreAuthorize("hasAuthority('admin:llm:view')")
    public ApiResult<List<LlmRoute>> routes() {
        return ApiResult.ok(llmAdminService.routes());
    }

    @PostMapping("/routes")
    @PreAuthorize("hasAuthority('admin:llm:edit')")
    public ApiResult<Void> createRoute(@Valid @RequestBody RouteSaveRequest request) {
        llmAdminService.saveRoute(null, request);
        return ApiResult.ok();
    }

    @PutMapping("/routes/{id}")
    @PreAuthorize("hasAuthority('admin:llm:edit')")
    public ApiResult<Void> updateRoute(@PathVariable Long id, @Valid @RequestBody RouteSaveRequest request) {
        llmAdminService.saveRoute(id, request);
        return ApiResult.ok();
    }
}
