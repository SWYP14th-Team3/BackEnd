package com.backend.analysis.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "requirement_evaluation")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequirementEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id", nullable = false, unique = true)
    private JobRequirement jobRequirement;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 30)
    private MatchStatus matchStatus;

    @Lob
    @Column(name = "resume_evidence", columnDefinition = "TEXT")
    private String resumeEvidence;

    @Lob
    @Column(name = "feedback", nullable = false, columnDefinition = "TEXT")
    private String feedback;

    @Lob
    @Column(name = "revision_suggestion", columnDefinition = "TEXT")
    private String revisionSuggestion;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private RequirementEvaluation(
            JobRequirement jobRequirement,
            MatchStatus matchStatus,
            String resumeEvidence,
            String feedback,
            String revisionSuggestion
    ) {
        this.jobRequirement = jobRequirement;
        this.matchStatus = matchStatus;
        this.resumeEvidence = resumeEvidence;
        this.feedback = feedback;
        this.revisionSuggestion = revisionSuggestion;
    }
}