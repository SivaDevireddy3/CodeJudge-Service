package com.siva.codejudge_service.entity;

import com.siva.codejudge_service.enums.Difficulty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "problems")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String constraints;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    private String topic;

    @Column(name = "time_limit_ms")
    private Integer timeLimitMs = 2000;

    @Column(name = "memory_limit_mb")
    private Integer memoryLimitMb = 256;

    private Integer points = 100;

    @Column(name = "acceptance_count")
    private Integer acceptanceCount = 0;

    @Column(name = "total_submissions")
    private Integer totalSubmissions = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TestCase> testCases = new ArrayList<>();

    public String getAcceptanceRate() {
        if (totalSubmissions == 0) return "0.0%";
        double rate = (acceptanceCount * 100.0) / totalSubmissions;
        return String.format("%.1f%%", rate);
    }
}
