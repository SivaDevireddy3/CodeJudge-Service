package com.siva.codejudge_service.judge;

import com.siva.codejudge_service.enums.Verdict;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeResult {
    private Verdict verdict;
    private Integer executionTimeMs;
    private Integer memoryUsedMb;
    private String  errorMessage;
    private String  actualOutput;
    private Integer failedTestCase;
}
