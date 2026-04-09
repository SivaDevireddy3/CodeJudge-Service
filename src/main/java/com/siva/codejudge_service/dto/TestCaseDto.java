package com.siva.codejudge_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseDto {

    @NotBlank
    private String input;

    @NotNull
    private String expectedOutput;

    private boolean hidden = false;
    private Integer orderIndex = 0;
}