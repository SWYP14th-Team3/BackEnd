package com.backend.analysis_20260710.domain;

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
@Table(name = "analysis_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "job_input_type", nullable = false, length = 20)
    private String jobInputType;

    @Column(name = "job_url", length = 1000)
    private String jobUrl;

    @Column(name = "job_platform", length = 100)
    private String jobPlatform;

    @Lob
    @Column(name = "job_posting_raw", columnDefinition = "LONGTEXT")
    private String jobPostingRaw;

    @Lob
    @Column(name = "resume_original_text", columnDefinition = "LONGTEXT")
    private String resumeOriginalText;

    @Lob
    @Column(name = "resume_current_text", columnDefinition = "LONGTEXT")
    private String resumeCurrentText;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "position_title", length = 255)
    private String positionTitle;

    @Column(name = "overall_level", nullable = false, length = 20)
    private String overallLevel;

    @Column(name = "red_count", nullable = false)
    private int redCount;

    @Column(name = "yellow_count", nullable = false)
    private int yellowCount;

    @Column(name = "green_count", nullable = false)
    private int greenCount;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "satisfaction", length = 20)
    private String satisfaction;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_saved_at")
    private LocalDateTime lastSavedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private AnalysisResult(
            String userId,
            String jobInputType,
            String jobUrl,
            String jobPlatform,
            String jobPostingRaw,
            String resumeOriginalText,
            String resumeCurrentText,
            String companyName,
            String positionTitle,
            String overallLevel,
            int redCount,
            int yellowCount,
            int greenCount
    ) {
        this.userId = userId;
        this.jobInputType = jobInputType;
        this.jobUrl = jobUrl;
        this.jobPlatform = jobPlatform;
        this.jobPostingRaw = jobPostingRaw;
        this.resumeOriginalText = resumeOriginalText;
        this.resumeCurrentText = resumeCurrentText;
        this.companyName = companyName;
        this.positionTitle = positionTitle;
        this.overallLevel = overallLevel;
        this.redCount = redCount;
        this.yellowCount = yellowCount;
        this.greenCount = greenCount;
        this.retryCount = 0;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSavedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
