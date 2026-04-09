package com.siva.codejudge_service.service;

import com.siva.codejudge_service.dto.CreateProblemRequest;
import com.siva.codejudge_service.dto.ProblemDetailResponse;
import com.siva.codejudge_service.dto.ProblemSummaryResponse;
import com.siva.codejudge_service.dto.SampleTestCaseDto;
import com.siva.codejudge_service.entity.Problem;
import com.siva.codejudge_service.entity.TestCase;
import com.siva.codejudge_service.entity.User;
import com.siva.codejudge_service.enums.Difficulty;
import com.siva.codejudge_service.enums.Verdict;
import com.siva.codejudge_service.repository.ProblemRepository;
import com.siva.codejudge_service.repository.SubmissionRepository;
import com.siva.codejudge_service.repository.TestCaseRepository;
import com.siva.codejudge_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private final ProblemRepository    problemRepository;
    private final TestCaseRepository   testCaseRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository       userRepository;

    @Transactional(readOnly = true)
    public Page<ProblemSummaryResponse> getProblems(
            String difficultyStr, String topic, String search,
            int page, int size) {

        Difficulty difficulty = null;
        if (difficultyStr != null && !difficultyStr.isBlank()) {
            try { difficulty = Difficulty.valueOf(difficultyStr.toUpperCase()); } catch (Exception ignored) {}
        }

        Page<Problem> problems = problemRepository.findFiltered(
                difficulty, topic, search,
                PageRequest.of(page, size, Sort.by("id")));

        Long currentUserId = getCurrentUserId();

        // BUG FIX 11: N+1 query — original called submissionRepository.findAccepted()
        // once per problem row (one extra DB query per problem). With 50 problems that's
        // 51 queries per page load.
        // Fix: fetch ALL accepted problem IDs for this user in ONE query upfront,
        // then check against the in-memory set — O(1) per problem.
        Set<Long> solvedProblemIds = Collections.emptySet();
        if (currentUserId != null) {
            solvedProblemIds = submissionRepository
                    .findSolvedProblemIdsByUser(currentUserId, Verdict.AC);
        }

        final Set<Long> solvedIds = solvedProblemIds;
        return problems.map(p -> toSummary(p, solvedIds));
    }

    @Transactional(readOnly = true)
    public ProblemDetailResponse getProblemById(Long id) {
        Problem p = problemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Problem not found: " + id));

        List<SampleTestCaseDto> samples = testCaseRepository
                .findByProblemIdAndHiddenFalseOrderByOrderIndexAsc(id)
                .stream()
                .map(tc -> SampleTestCaseDto.builder()
                        .input(tc.getInput())
                        .expectedOutput(tc.getExpectedOutput())
                        .orderIndex(tc.getOrderIndex())
                        .build())
                .collect(Collectors.toList());

        return ProblemDetailResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .description(p.getDescription())
                .constraints(p.getConstraints())
                .difficulty(p.getDifficulty())
                .topic(p.getTopic())
                .timeLimitMs(p.getTimeLimitMs())
                .memoryLimitMb(p.getMemoryLimitMb())
                .points(p.getPoints())
                .acceptanceRate(p.getAcceptanceRate())
                .totalSubmissions(p.getTotalSubmissions())
                .sampleTestCases(samples)
                .createdAt(p.getCreatedAt())
                .build();
    }

    @Transactional
    public ProblemDetailResponse createProblem(CreateProblemRequest req) {
        String currentUsername = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        User admin = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Problem problem = Problem.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .constraints(req.getConstraints())
                .difficulty(req.getDifficulty())
                .topic(req.getTopic())
                .timeLimitMs(req.getTimeLimitMs() != null ? req.getTimeLimitMs() : 2000)
                .memoryLimitMb(req.getMemoryLimitMb() != null ? req.getMemoryLimitMb() : 256)
                .points(req.getPoints() != null ? req.getPoints() : 100)
                .createdBy(admin)
                .build();

        problem = problemRepository.save(problem);

        if (req.getTestCases() != null) {
            final Problem saved = problem;
            List<TestCase> testCases = req.getTestCases().stream()
                    .map(tc -> TestCase.builder()
                            .problem(saved)
                            .input(tc.getInput())
                            .expectedOutput(tc.getExpectedOutput())
                            .hidden(tc.isHidden())
                            .orderIndex(tc.getOrderIndex() != null ? tc.getOrderIndex() : 0)
                            .build())
                    .collect(Collectors.toList());
            testCaseRepository.saveAll(testCases);
        }

        return getProblemById(problem.getId());
    }

    @Transactional
    public void deleteProblem(Long id) {
        Problem p = problemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Problem not found: " + id));
        p.setActive(false);
        problemRepository.save(p);
    }

    private ProblemSummaryResponse toSummary(Problem p, Set<Long> solvedIds) {
        return ProblemSummaryResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .difficulty(p.getDifficulty())
                .topic(p.getTopic())
                .acceptanceRate(p.getAcceptanceRate())
                .points(p.getPoints())
                .solvedByCurrentUser(solvedIds.contains(p.getId()))
                .build();
    }

    private Long getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            // BUG FIX 12: original swallowed all exceptions including real auth errors.
            // Anonymous users have principal "anonymousUser" (a String), not a username.
            // Guard against that case explicitly instead of catching everything.
            if (auth == null || !auth.isAuthenticated()
                    || "anonymousUser".equals(auth.getPrincipal())) {
                return null;
            }
            return userRepository.findByUsername(auth.getName())
                    .map(User::getId)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}