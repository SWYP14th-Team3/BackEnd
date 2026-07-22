package com.backend.analysis_20260710.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id", nullable = false)
    private JobRequirement requirement;

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
            JobRequirement requirement,
            String matchStatus,
            String resumeEvidence,
            String feedback,
            String revisionSuggestion
    ) {
        this.requirement = requirement;
        this.matchStatus = matchStatus;
        this.resumeEvidence = resumeEvidence;
        this.feedback = feedback;
        this.revisionSuggestion = revisionSuggestion;
    }

    @PrePersist
    @PreUpdate
    void updateTime() {
        this.updatedAt = LocalDateTime.now();
    }
}