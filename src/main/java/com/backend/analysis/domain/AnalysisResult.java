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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_num", nullable = false)
    private UserResume userResume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jd_num", nullable = false)
    private JobDescription jobDescription;

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
            UserResume userResume,
            JobDescription jobDescription,
            OverallLevel overallLevel,
            Integer redCount,
            Integer yellowCount,
            Integer greenCount
    ) {
        this.user = user;
        this.userResume = userResume;
        this.jobDescription = jobDescription;
        this.overallLevel = overallLevel;
        this.redCount = redCount;
        this.yellowCount = yellowCount;
        this.greenCount = greenCount;
        this.retryCount = 0;
        this.satisfaction = null;
    }

    public void markSaved(LocalDateTime savedAt) {
        this.lastSavedAt = savedAt;
    }

    public void updateSatisfaction(Satisfaction satisfaction) {
        this.satisfaction = satisfaction;
    }

    public void delete(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
