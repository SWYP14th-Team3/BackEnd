
package com.backend.analysis_20260710.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "job_requirement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false)
    private AnalysisResult analysisResult;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Lob
    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    @Lob
    @Column(name = "source_text", columnDefinition = "LONGTEXT")
    private String sourceText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private JobRequirement(
            AnalysisResult analysisResult,
            String category,
            String title,
            String description,
            String sourceText
    ) {
        this.analysisResult = analysisResult;
        this.category = category;
        this.title = title;
        this.description = description;
        this.sourceText = sourceText;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}