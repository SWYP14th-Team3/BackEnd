package com.backend.analysis.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_result_id", nullable = false)
    private AnalysisResult analysisResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 30)
    private MatchStatus matchStatus;

    @Column(name = "display_title", length = 200)
    private String displayTitle;

    @Lob
    @Column(name = "resume_evidence", columnDefinition = "TEXT")
    private String resumeEvidence;

    @Lob
    @Column(name = "judge_reason", columnDefinition = "TEXT")
    private String judgeReason;

    @Lob
    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Lob
    @Column(name = "revision_suggestion", columnDefinition = "TEXT")
    private String revisionSuggestion;

    @Column(name = "effect_score")
    private Integer effectScore;

    @Column(name = "effort_score")
    private Integer effortScore;

    @Column(name = "priority_score", precision = 10, scale = 4)
    private BigDecimal priorityScore;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private RequirementEvaluation(
            JobRequirement jobRequirement,
            MatchStatus matchStatus,
            String displayTitle,
            String resumeEvidence,
            String judgeReason,
            String feedback,
            String revisionSuggestion,
            Integer effectScore,
            Integer effortScore,
            BigDecimal priorityScore,
            Integer sortOrder
    ) {
        this.jobRequirement = jobRequirement;
        this.analysisResult = jobRequirement.getAnalysisResult();
        this.matchStatus = matchStatus;
        this.displayTitle = displayTitle != null ? displayTitle : jobRequirement.getTitle();
        this.resumeEvidence = resumeEvidence;
        this.judgeReason = judgeReason;
        this.feedback = feedback;
        this.revisionSuggestion = revisionSuggestion;
        this.effectScore = effectScore;
        this.effortScore = effortScore;
        this.priorityScore = priorityScore;
        this.sortOrder = sortOrder;
    }
}
