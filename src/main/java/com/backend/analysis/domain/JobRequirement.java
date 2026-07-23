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
    @Column(name = "requirement_type", nullable = false, length = 20)
    private RequirementType requirementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private RequirementCategory category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Lob
    @Column(name = "jd_evidence", columnDefinition = "TEXT")
    private String jdEvidence;

    @Column(name = "input_order", nullable = false)
    private Integer inputOrder;

    @Builder
    private JobRequirement(
            AnalysisResult analysisResult,
            RequirementType requirementType,
            RequirementCategory category,
            String title,
            String description,
            String jdEvidence,
            String sourceText,
            Integer inputOrder
    ) {
        this.analysisResult = analysisResult;
        this.requirementType = requirementType != null ? requirementType : RequirementType.REQUIRED;
        this.category = category;
        this.title = title;
        this.description = description;
        this.jdEvidence = jdEvidence != null ? jdEvidence : sourceText;
        this.inputOrder = inputOrder != null ? inputOrder : 0;
    }

    public String getSourceText() {
        return jdEvidence;
    }
}
