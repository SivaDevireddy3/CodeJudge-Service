package com.siva.codejudge_service.service;

import com.siva.codejudge_service.dto.AuthResponse;
import com.siva.codejudge_service.dto.LoginRequest;
import com.siva.codejudge_service.dto.RegisterRequest;
import com.siva.codejudge_service.entity.User;
import com.siva.codejudge_service.enums.Role;
import com.siva.codejudge_service.repository.UserRepository;
import com.siva.codejudge_service.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final AuthenticationManager authManager;
    private final JwtUtils              jwtUtils;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername()))
            throw new IllegalArgumentException("Username already taken: " + req.getUsername());
        if (userRepository.existsByEmail(req.getEmail()))
            throw new IllegalArgumentException("Email already registered: " + req.getEmail());

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .displayName(req.getDisplayName() != null ? req.getDisplayName() : req.getUsername())
                .role(Role.USER)
                .build();

        userRepository.save(user);
        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));

        UserDetails ud = (UserDetails) auth.getPrincipal();
        User user = userRepository.findByUsername(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        // BUG FIX 16: Original built a temporary Spring UserDetails using
        // org.springframework.security.core.userdetails.User.builder()
        // and passed .roles(user.getRole().name()) to it.
        // .roles() prepends "ROLE_" automatically, so the role stored in the JWT
        // would be "ROLE_USER" / "ROLE_ADMIN" instead of "USER" / "ADMIN".
        // But generateToken() stores it as-is, and UserDetailsServiceImpl
        // creates authorities as "ROLE_" + role.name() — so the JWT role and the
        // Spring Security authority were inconsistent, making role-based checks
        // in the filter unreliable.
        // Fix: build UserDetails with explicit authorities to avoid the double-prefix.
        UserDetails ud = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();

        String token = jwtUtils.generateToken(ud, user.getId(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .displayName(user.getDisplayName())
                .build();
    }
}