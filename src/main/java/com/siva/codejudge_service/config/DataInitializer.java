package com.siva.codejudge_service.config;

import com.siva.codejudge_service.entity.User;
import com.siva.codejudge_service.enums.Role;
import com.siva.codejudge_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:Admin@1234}")
    private String adminPassword;

    @Value("${app.admin.email:admin@codejudge.local}")
    private String adminEmail;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(adminUsername)) {
            log.info("Admin account '{}' already exists — skipping seed.", adminUsername);
            return;
        }

        User admin = User.builder()
                .username(adminUsername)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .displayName("Administrator")
                .role(Role.ADMIN)
                .enabled(true)
                .build();

        userRepository.save(admin);
        log.info("✔  Default admin account '{}' created. Change the password immediately!", adminUsername);
    }
}

