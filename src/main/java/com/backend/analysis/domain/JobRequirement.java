package com.backend.analysis.domain;

import com.backend.global.common.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "job_requirement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRequirement extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false)
    private AnalysisResult analysisResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private RequirementCategory category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Lob
    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    @Builder
    private JobRequirement(
            AnalysisResult analysisResult,
            RequirementCategory category,
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
}