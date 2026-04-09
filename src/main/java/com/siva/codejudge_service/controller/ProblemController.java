package com.siva.codejudge_service.controller;

import com.siva.codejudge_service.dto.CreateProblemRequest;
import com.siva.codejudge_service.dto.ProblemDetailResponse;
import com.siva.codejudge_service.dto.ProblemSummaryResponse;
import com.siva.codejudge_service.service.ProblemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    @GetMapping
    public ResponseEntity<Page<ProblemSummaryResponse>> list(
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                problemService.getProblems(difficulty, topic, search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProblemDetailResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(problemService.getProblemById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProblemDetailResponse> create(
            @Valid @RequestBody CreateProblemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(problemService.createProblem(req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return ResponseEntity.noContent().build();
    }
}
