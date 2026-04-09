package com.siva.codejudge_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntry {
    private Integer rank;
    private Long    userId;
    private String  username;
    private String  displayName;
    private Integer totalScore;
    private Integer problemsSolved;
    private Integer streakDays;
}
