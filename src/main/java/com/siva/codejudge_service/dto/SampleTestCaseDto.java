package com.siva.codejudge_service.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleTestCaseDto {
    private String input;
    private String expectedOutput;
    private Integer orderIndex;
}
