package com.siva.codejudge_service.dto;

import com.siva.codejudge_service.enums.Language;
import com.siva.codejudge_service.enums.Verdict;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponse {
    private Long id;
    private String problemTitle;
    private Long problemId;
    private String username;
    private Language language;
    private Verdict verdict;
    private Integer executionTimeMs;
    private Integer memoryUsedMb;
    private String errorMessage;
    private Integer failedTestCase;
    private LocalDateTime submittedAt;
}
