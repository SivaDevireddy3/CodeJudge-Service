package com.siva.codejudge_service.dto;

import com.siva.codejudge_service.enums.Difficulty;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemDetailResponse {
    private Long id;
    private String title;
    private String description;
    private String constraints;
    private Difficulty difficulty;
    private String topic;
    private Integer timeLimitMs;
    private Integer memoryLimitMb;
    private Integer points;
    private String acceptanceRate;
    private Integer totalSubmissions;
    private List<SampleTestCaseDto> sampleTestCases;
    private LocalDateTime createdAt;
}
