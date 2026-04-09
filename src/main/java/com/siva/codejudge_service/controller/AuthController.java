package com.siva.codejudge_service.controller;

import com.siva.codejudge_service.dto.AuthResponse;
import com.siva.codejudge_service.dto.LoginRequest;
import com.siva.codejudge_service.dto.RegisterRequest;
import com.siva.codejudge_service.entity.User;
import com.siva.codejudge_service.repository.UserRepository;
import com.siva.codejudge_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService    authService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication auth) {
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Map<String, Object> response = new HashMap<>();
        response.put("userId",         user.getId());
        response.put("username",        user.getUsername());
        response.put("email",           user.getEmail());
        response.put("role",            user.getRole().name());
        response.put("displayName",     user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
        response.put("totalScore",      user.getTotalScore());
        response.put("problemsSolved",  user.getProblemsSolved());
        return ResponseEntity.ok(response);
    }
}