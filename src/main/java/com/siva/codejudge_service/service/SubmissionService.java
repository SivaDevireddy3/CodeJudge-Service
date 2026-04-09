package com.siva.codejudge_service.service;

import com.siva.codejudge_service.dto.SubmissionResponse;
import com.siva.codejudge_service.dto.SubmitRequest;
import com.siva.codejudge_service.entity.Problem;
import com.siva.codejudge_service.entity.Submission;
import com.siva.codejudge_service.entity.TestCase;
import com.siva.codejudge_service.entity.User;
import com.siva.codejudge_service.enums.Verdict;
import com.siva.codejudge_service.judge.DockerJudgeEngine;
import com.siva.codejudge_service.judge.JudgeResult;
import com.siva.codejudge_service.repository.ProblemRepository;
import com.siva.codejudge_service.repository.SubmissionRepository;
import com.siva.codejudge_service.repository.TestCaseRepository;
import com.siva.codejudge_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository    problemRepository;
    private final TestCaseRepository   testCaseRepository;
    private final UserRepository       userRepository;
    private final DockerJudgeEngine    judgeEngine;

    // BUG FIX 9: The entire submit() method was wrapped in a single @Transactional.
    // This held an open database connection across the entire Docker execution (potentially
    // 2-10+ seconds), exhausting the HikariCP connection pool under any load.
    // Fix: split into two transactions — one to prepare + save PENDING, one to persist results.
    public SubmissionResponse submit(SubmitRequest req) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        // TX 1: load entities, check prior AC, save PENDING submission, increment counter
        SubmitContext ctx = prepareSubmission(username, req);

        // Run judge OUTSIDE any transaction — no DB connection held during Docker execution
        JudgeResult result = judgeEngine.judge(
                req.getCode(),
                req.getLanguage(),
                ctx.testCases,
                ctx.problem.getTimeLimitMs(),
                ctx.problem.getMemoryLimitMb()
        );

        // TX 2: persist the verdict and award points if first AC
        SubmissionResponse response = persistResult(ctx, result, username);

        log.info("Submission {} | problem={} | user={} | verdict={} | time={}ms",
                ctx.submissionId, ctx.problem.getId(), username,
                result.getVerdict(), result.getExecutionTimeMs());

        return response;
    }

    @Transactional
    protected SubmitContext prepareSubmission(String username, SubmitRequest req) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Problem problem = problemRepository.findById(req.getProblemId())
                .orElseThrow(() -> new RuntimeException("Problem not found: " + req.getProblemId()));

        // Check for prior AC BEFORE saving the new submission
        boolean alreadySolved = !submissionRepository
                .findAccepted(user.getId(), problem.getId(), Verdict.AC).isEmpty();

        Submission submission = Submission.builder()
                .user(user)
                .problem(problem)
                .code(req.getCode())
                .language(req.getLanguage())
                .verdict(Verdict.PENDING)
                .build();
        submission = submissionRepository.save(submission);

        problem.setTotalSubmissions(problem.getTotalSubmissions() + 1);
        problemRepository.save(problem);

        List<TestCase> testCases = testCaseRepository
                .findByProblemIdOrderByOrderIndexAsc(problem.getId());

        return new SubmitContext(submission.getId(), user, problem, testCases, alreadySolved);
    }

    @Transactional
    protected SubmissionResponse persistResult(SubmitContext ctx, JudgeResult result, String username) {
        Submission submission = submissionRepository.findById(ctx.submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found: " + ctx.submissionId));

        submission.setVerdict(result.getVerdict());
        submission.setExecutionTimeMs(result.getExecutionTimeMs());
        submission.setMemoryUsedMb(result.getMemoryUsedMb());
        submission.setErrorMessage(result.getErrorMessage());
        submission.setFailedTestCase(result.getFailedTestCase());
        submission = submissionRepository.save(submission);

        if (result.getVerdict() == Verdict.AC && !ctx.alreadySolved) {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));
            user.setProblemsSolved(user.getProblemsSolved() + 1);
            user.setTotalScore(user.getTotalScore() + ctx.problem.getPoints());
            userRepository.save(user);

            ctx.problem.setAcceptanceCount(ctx.problem.getAcceptanceCount() + 1);
            problemRepository.save(ctx.problem);
        }

        return toResponse(submission);
    }

    /** Small value-holder to pass state between the two transactions. */
    private static class SubmitContext {
        final Long        submissionId;
        final User        user;
        final Problem     problem;
        final List<TestCase> testCases;
        final boolean     alreadySolved;

        SubmitContext(Long submissionId, User user, Problem problem,
                      List<TestCase> testCases, boolean alreadySolved) {
            this.submissionId  = submissionId;
            this.user          = user;
            this.problem       = problem;
            this.testCases     = testCases;
            this.alreadySolved = alreadySolved;
        }
    }

    @Transactional(readOnly = true)
    public Page<SubmissionResponse> getMySubmissions(int page, int size) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return submissionRepository
                .findByUserIdOrderBySubmittedAtDesc(user.getId(), PageRequest.of(page, size))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getById(Long id) {
        // BUG FIX 10: No ownership check — any authenticated user could read any submission
        // by guessing an ID. Added check: only the owner (or ADMIN) can fetch a submission.
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User caller = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        Submission s = submissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Submission not found: " + id));

        boolean isOwner = s.getUser().getId().equals(caller.getId());
        boolean isAdmin = caller.getRole().name().equals("ADMIN");

        if (!isOwner && !isAdmin) {
            throw new RuntimeException("Access denied: submission not found: " + id);
        }

        return toResponse(s);
    }

    private SubmissionResponse toResponse(Submission s) {
        return SubmissionResponse.builder()
                .id(s.getId())
                .problemTitle(s.getProblem().getTitle())
                .problemId(s.getProblem().getId())
                .username(s.getUser().getUsername())
                .language(s.getLanguage())
                .verdict(s.getVerdict())
                .executionTimeMs(s.getExecutionTimeMs())
                .memoryUsedMb(s.getMemoryUsedMb())
                .errorMessage(s.getErrorMessage())
                .failedTestCase(s.getFailedTestCase())
                .submittedAt(s.getSubmittedAt())
                .build();
    }
}