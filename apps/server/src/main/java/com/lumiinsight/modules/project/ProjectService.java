package com.lumiinsight.modules.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.common.security.AuthUser;
import com.lumiinsight.common.security.SecurityUtils;
import com.lumiinsight.modules.audit.AuditService;
import com.lumiinsight.modules.project.dto.ProjectSaveRequest;
import com.lumiinsight.modules.project.dto.ProjectView;
import com.lumiinsight.modules.project.entity.Project;
import com.lumiinsight.modules.project.entity.ProjectMember;
import com.lumiinsight.modules.project.mapper.ProjectMapper;
import com.lumiinsight.modules.project.mapper.ProjectMemberMapper;
import com.lumiinsight.modules.review.entity.Review;
import com.lumiinsight.modules.review.mapper.ReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ReviewMapper reviewMapper;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public ProjectService(
            ProjectMapper projectMapper,
            ProjectMemberMapper memberMapper,
            ReviewMapper reviewMapper,
            ObjectMapper objectMapper,
            AuditService auditService
    ) {
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.reviewMapper = reviewMapper;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    public Page<ProjectView> page(long page, long size) {
        AuthUser user = SecurityUtils.requireUser();
        LambdaQueryWrapper<Project> q = new LambdaQueryWrapper<Project>().orderByDesc(Project::getId);
        if (!SecurityUtils.isAdmin()) {
            List<Long> ids = visibleProjectIds(user.getUserId());
            if (ids.isEmpty()) {
                return new Page<>(page, size, 0);
            }
            q.in(Project::getId, ids);
        }
        Page<Project> raw = projectMapper.selectPage(new Page<>(page, size), q);
        Page<ProjectView> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        out.setRecords(raw.getRecords().stream().map(this::toView).toList());
        return out;
    }

    public ProjectView detail(Long id) {
        return toView(requireVisible(id));
    }

    @Transactional
    public Long create(ProjectSaveRequest req) {
        AuthUser user = SecurityUtils.requireUser();
        Project project = new Project();
        apply(project, req);
        project.setOwnerId(user.getUserId());
        projectMapper.insert(project);
        replaceMembers(project.getId(), user.getUserId(), req.getMemberIds());
        auditService.record("project.create", "project", project.getName());
        return project.getId();
    }

    @Transactional
    public void update(Long id, ProjectSaveRequest req) {
        Project project = requireVisible(id);
        if (!SecurityUtils.isAdmin() && !project.getOwnerId().equals(SecurityUtils.requireUser().getUserId())) {
            throw BizException.of("FORBIDDEN", "只有负责人可以修改项目");
        }
        apply(project, req);
        projectMapper.updateById(project);
        replaceMembers(id, project.getOwnerId(), req.getMemberIds());
        auditService.record("project.update", "project", project.getName());
    }

    @Transactional
    public void delete(Long id) {
        Project project = requireVisible(id);
        if (!SecurityUtils.isAdmin() && !project.getOwnerId().equals(SecurityUtils.requireUser().getUserId())) {
            throw BizException.of("FORBIDDEN", "只有负责人可以删除项目");
        }
        projectMapper.deleteById(id);
        auditService.record("project.delete", "project", project.getName());
    }

    public Project requireVisible(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw BizException.of("NOT_FOUND", "项目不存在");
        }
        if (SecurityUtils.isAdmin()) {
            return project;
        }
        Long uid = SecurityUtils.requireUser().getUserId();
        if (project.getOwnerId().equals(uid)) {
            return project;
        }
        Long member = memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, id)
                .eq(ProjectMember::getUserId, uid));
        if (member == null || member == 0) {
            throw BizException.of("FORBIDDEN", "无权访问该项目");
        }
        return project;
    }

    private List<Long> visibleProjectIds(Long userId) {
        List<Long> ids = new ArrayList<>();
        projectMapper.selectList(new LambdaQueryWrapper<Project>().eq(Project::getOwnerId, userId))
                .forEach(p -> ids.add(p.getId()));
        memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId))
                .forEach(m -> ids.add(m.getProjectId()));
        return ids.stream().distinct().toList();
    }

    private void replaceMembers(Long projectId, Long ownerId, List<Long> memberIds) {
        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, projectId));
        List<Long> all = new ArrayList<>();
        all.add(ownerId);
        if (memberIds != null) {
            all.addAll(memberIds);
        }
        all.stream().distinct().forEach(uid -> {
            ProjectMember m = new ProjectMember();
            m.setProjectId(projectId);
            m.setUserId(uid);
            memberMapper.insert(m);
        });
    }

    private void apply(Project project, ProjectSaveRequest req) {
        project.setName(req.getName().trim());
        project.setCategory(req.getCategory());
        project.setBrand(req.getBrand());
        project.setMainModel(req.getMainModel());
        project.setCompetitors(writeJson(req.getCompetitors()));
        project.setKeywords(writeJson(req.getKeywords()));
        project.setPlatforms(writeJson(req.getPlatforms()));
        project.setTimeStart(req.getTimeStart());
        project.setTimeEnd(req.getTimeEnd());
    }

    private ProjectView toView(Project p) {
        long reviews = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getProjectId, p.getId())
                .eq(Review::getCounted, 1));
        return ProjectView.builder()
                .id(p.getId())
                .name(p.getName())
                .category(p.getCategory())
                .brand(p.getBrand())
                .mainModel(p.getMainModel())
                .competitors(readJson(p.getCompetitors()))
                .keywords(readJson(p.getKeywords()))
                .platforms(readJson(p.getPlatforms()))
                .timeStart(p.getTimeStart())
                .timeEnd(p.getTimeEnd())
                .ownerId(p.getOwnerId())
                .createdAt(p.getCreatedAt())
                .reviewCount(reviews)
                .build();
    }

    private String writeJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> readJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
