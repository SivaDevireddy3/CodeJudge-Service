package com.siva.codejudge_service.dto;

import com.siva.codejudge_service.enums.Difficulty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemSummaryResponse {
    private Long id;
    private String title;
    private Difficulty difficulty;
    private String topic;
    private String acceptanceRate;
    private Integer points;
    private boolean solvedByCurrentUser;
}
