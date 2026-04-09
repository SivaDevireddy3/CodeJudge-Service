package com.siva.codejudge_service.repository;

import com.siva.codejudge_service.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {
    List<TestCase> findByProblemIdAndHiddenFalseOrderByOrderIndexAsc(Long problemId);
    List<TestCase> findByProblemIdOrderByOrderIndexAsc(Long problemId);
}
