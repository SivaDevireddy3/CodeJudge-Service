package com.siva.codejudge_service.dto;

import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor

public class AuthResponse {
    private String token;
    private String tokenType = "Bearer";
    private Long   userId;
    private String username;
    private String email;
    private String role;
    private String displayName;
}
