package com.siva.codejudge_service.repository;

import com.siva.codejudge_service.entity.Submission;
import com.siva.codejudge_service.enums.Verdict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    Page<Submission> findByUserIdOrderBySubmittedAtDesc(Long userId, Pageable pageable);

    List<Submission> findByUserIdAndProblemIdOrderBySubmittedAtDesc(Long userId, Long problemId);

    @Query("SELECT s FROM Submission s WHERE s.user.id = :userId AND s.problem.id = :problemId AND s.verdict = :verdict")
    List<Submission> findAccepted(
            @Param("userId")    Long userId,
            @Param("problemId") Long problemId,
            @Param("verdict")   Verdict verdict);

    @Query("SELECT DISTINCT s.problem.id FROM Submission s WHERE s.user.id = :userId AND s.verdict = :verdict")
    Set<Long> findSolvedProblemIdsByUser(
            @Param("userId")  Long userId,
            @Param("verdict") Verdict verdict);

    long countByUserIdAndVerdict(Long userId, Verdict verdict);
}