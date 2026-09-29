package com.lumiinsight.modules.project;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.project.dto.ProjectSaveRequest;
import com.lumiinsight.modules.project.dto.ProjectView;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('project:view')")
    public ApiResult<Page<ProjectView>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResult.ok(projectService.page(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('project:view')")
    public ApiResult<ProjectView> detail(@PathVariable Long id) {
        return ApiResult.ok(projectService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('project:edit')")
    public ApiResult<Map<String, Long>> create(@Valid @RequestBody ProjectSaveRequest request) {
        return ApiResult.ok(Map.of("id", projectService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('project:edit')")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody ProjectSaveRequest request) {
        projectService.update(id, request);
        return ApiResult.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('project:delete')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResult.ok();
    }
}
