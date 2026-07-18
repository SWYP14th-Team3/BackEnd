package com.backend.analysis.domain;

import com.backend.global.common.entity.BaseTimeEntity;
import com.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "analysis_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisResult extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인 후 분석하는 흐름이면 nullable = false
    // 비회원 분석 결과 저장까지 지원한다면 nullable = true 검토 필요
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_input_type", nullable = false, length = 20)
    private JobInputType jobInputType;

    @Column(name = "job_url", length = 1000)
    private String jobUrl;

    @Column(name = "job_platform", length = 100)
    private String jobPlatform;

    @Lob
    @Column(name = "job_posting_raw", nullable = false, columnDefinition = "TEXT")
    private String jobPostingRaw;

    @Lob
    @Column(name = "resume_original_text", nullable = false, columnDefinition = "TEXT")
    private String resumeOriginalText;

    @Lob
    @Column(name = "resume_current_text", nullable = false, columnDefinition = "TEXT")
    private String resumeCurrentText;

    @Column(name = "company_name", length = 100)
    private String companyName;

    @Column(name = "position_title", length = 100)
    private String positionTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_level", nullable = false, length = 20)
    private OverallLevel overallLevel;

    @Column(name = "red_count", nullable = false)
    private Integer redCount;

    @Column(name = "yellow_count", nullable = false)
    private Integer yellowCount;

    @Column(name = "green_count", nullable = false)
    private Integer greenCount;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "satisfaction", length = 20)
    private Satisfaction satisfaction;

    @Column(name = "last_saved_at")
    private LocalDateTime lastSavedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private AnalysisResult(
            User user,
            JobInputType jobInputType,
            String jobUrl,
            String jobPlatform,
            String jobPostingRaw,
            String resumeOriginalText,
            String resumeCurrentText,
            String companyName,
            String positionTitle,
            OverallLevel overallLevel,
            Integer redCount,
            Integer yellowCount,
            Integer greenCount
    ) {
        this.user = user;
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
        this.satisfaction = null;
    }

    public void updateResumeCurrentText(String resumeCurrentText, LocalDateTime savedAt) {
        this.resumeCurrentText = resumeCurrentText;
        this.lastSavedAt = savedAt;
    }

    public void updateSatisfaction(Satisfaction satisfaction) {
        this.satisfaction = satisfaction;
    }

    public void delete(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
