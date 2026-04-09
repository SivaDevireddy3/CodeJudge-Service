package com.siva.codejudge_service.dto;

import com.siva.codejudge_service.enums.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitRequest {

    @NotNull(message = "Problem ID is required")
    private Long problemId;

    @NotBlank(message = "Code is required")
    @Size(max = 65536, message = "Code must not exceed 64KB")
    private String code;

    @NotNull(message = "Language is required")
    private Language language;
}