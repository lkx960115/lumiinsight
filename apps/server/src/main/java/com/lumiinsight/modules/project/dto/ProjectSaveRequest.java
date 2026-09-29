package com.lumiinsight.modules.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class ProjectSaveRequest {
    @NotBlank(message = "项目名称不能为空")
    private String name;
    private String category;
    private String brand;
    private String mainModel;
    private List<String> competitors;
    private List<String> keywords;
    private List<String> platforms;
    private LocalDate timeStart;
    private LocalDate timeEnd;
    private List<Long> memberIds;
}
