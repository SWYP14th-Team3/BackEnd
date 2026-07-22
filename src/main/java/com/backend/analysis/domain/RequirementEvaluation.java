package com.backend.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "requirement_evaluation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequirementEvaluation {

    // 요건 평가 row의 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 평가 대상인 job_requirement row ID
    @Column(name = "requirement_id", nullable = false)
    private Long requirementId;

    // 분석 결과별 평가를 바로 조회하기 위한 ID
    @Column(name = "analysis_result_id", nullable = false)
    private Long analysisResultId;

    // CONFIRMED / NEEDS_IMPROVEMENT / MISSING
    @Column(name = "match_status", nullable = false, length = 50)
    private String matchStatus;

    @Lob
    @Column(name = "resume_evidence", columnDefinition = "LONGTEXT")
    private String resumeEvidence;

    @Lob
    @Column(name = "feedback", columnDefinition = "LONGTEXT")
    private String feedback;

    @Lob
    @Column(name = "revision_suggestion", columnDefinition = "LONGTEXT")
    private String revisionSuggestion;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private RequirementEvaluation(
            Long requirementId,
            Long analysisResultId,
            String matchStatus,
            String resumeEvidence,
            String feedback,
            String revisionSuggestion
    ) {
        this.requirementId = requirementId;
        this.analysisResultId = analysisResultId;
        this.matchStatus = matchStatus;
        this.resumeEvidence = resumeEvidence;
        this.feedback = feedback;
        this.revisionSuggestion = revisionSuggestion;
    }

    public void updateReanalysis(
            String matchStatus,
            String resumeEvidence,
            String feedback,
            String revisionSuggestion
    ) {
        // 재분석 응답 기준으로 기존 평가 내용을 갱신
        this.matchStatus = matchStatus;
        this.resumeEvidence = resumeEvidence;
        this.feedback = feedback;
        this.revisionSuggestion = revisionSuggestion;
    }

    @PrePersist
    @PreUpdate
    void updateTime() {
        // 평가가 저장되거나 수정될 때 갱신
        this.updatedAt = LocalDateTime.now();
    }
}
