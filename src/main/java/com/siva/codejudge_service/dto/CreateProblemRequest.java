package com.siva.codejudge_service.dto;

import com.siva.codejudge_service.enums.Difficulty;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProblemRequest {
    @NotBlank @Size(max=200) private String title;
    @NotBlank private String description;
    private String constraints;
    @NotNull private Difficulty difficulty;
    private String topic;
    @Min(100) @Max(10000) private Integer timeLimitMs = 2000;
    @Min(32)  @Max(1024)  private Integer memoryLimitMb = 256;
    @Min(10)  @Max(1000)  private Integer points = 100;
    private List<TestCaseDto> testCases;
}
