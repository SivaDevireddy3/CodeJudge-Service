package com.siva.codejudge_service.controller;

import com.siva.codejudge_service.dto.SubmissionResponse;
import com.siva.codejudge_service.dto.SubmitRequest;
import com.siva.codejudge_service.service.SubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    public ResponseEntity<SubmissionResponse> submit(
            @Valid @RequestBody SubmitRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(submissionService.submit(req));
    }

    @GetMapping("/mine")
    public ResponseEntity<Page<SubmissionResponse>> mine(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(submissionService.getMySubmissions(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubmissionResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(submissionService.getById(id));
    }
}
