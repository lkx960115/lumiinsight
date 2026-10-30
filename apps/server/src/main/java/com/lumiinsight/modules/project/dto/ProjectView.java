package com.lumiinsight.modules.project.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ProjectView {
    private Long id;
    private String name;
    private String category;
    private String brand;
    private String mainModel;
    private List<String> competitors;
    private List<String> keywords;
    private List<String> platforms;
    private LocalDate timeStart;
    private LocalDate timeEnd;
    private Long ownerId;
    private LocalDateTime createdAt;
    private long reviewCount;
}
