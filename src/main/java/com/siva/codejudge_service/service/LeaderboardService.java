package com.siva.codejudge_service.service;

import com.siva.codejudge_service.dto.LeaderboardEntry;
import com.siva.codejudge_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> getLeaderboard() {
        // BUG FIX 18: findTopByScore() loaded ALL users into memory and then called
        // .limit(50) in Java. If the platform has 10,000 users, all 10,000 rows are
        // fetched from DB and discarded. Fix: push the LIMIT into the DB query via Pageable.
        AtomicInteger rank = new AtomicInteger(1);

        return userRepository.findTopByScore(PageRequest.of(0, 50))
                .stream()
                .map(u -> LeaderboardEntry.builder()
                        .rank(rank.getAndIncrement())
                        .userId(u.getId())
                        .username(u.getUsername())
                        .displayName(u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                        .totalScore(u.getTotalScore())
                        .problemsSolved(u.getProblemsSolved())
                        .streakDays(u.getStreakDays())
                        .build())
                .collect(Collectors.toList());
    }
}