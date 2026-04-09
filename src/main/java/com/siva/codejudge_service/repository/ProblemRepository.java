package com.siva.codejudge_service.repository;

import com.siva.codejudge_service.entity.Problem;
import com.siva.codejudge_service.enums.Difficulty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    List<Problem> findByActiveTrue();
    Page<Problem> findByActiveTrue(Pageable pageable);
    List<Problem> findByDifficultyAndActiveTrue(Difficulty difficulty);

    @Query("""
        SELECT p FROM Problem p
        WHERE p.active = true
        AND (:difficulty IS NULL OR p.difficulty = :difficulty)
        AND (:topic IS NULL OR p.topic = :topic)
        AND LOWER(p.title) LIKE LOWER(CONCAT('%', COALESCE(:search, ''), '%'))
    """)
    Page<Problem> findFiltered(
            @Param("difficulty") Difficulty difficulty,
            @Param("topic") String topic,
            @Param("search") String search,
            Pageable pageable);
}