package com.backend.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    // 분석 결과 한 건의 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 분석을 요청한 사용자 ID
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 저장된 이력서 요약 row ID
    @Column(name = "resume_num", nullable = false)
    private Long resumeNum;

    // 저장된 채용공고 요약 row ID
    @Column(name = "jd_num", nullable = false)
    private Long jdNum;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "position_title", length = 255)
    private String positionTitle;

    @Column(name = "overall_level", nullable = false, length = 20)
    private String overallLevel;

    // MISSING / NEEDS_IMPROVEMENT / CONFIRMED 개수
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

    @Column(name = "last_saved_at", nullable = false)
    private LocalDateTime lastSavedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private AnalysisResult(
            Long userId,
            Long resumeNum,
            Long jdNum,
            String companyName,
            String positionTitle,
            String overallLevel,
            int redCount,
            int yellowCount,
            int greenCount
    ) {
        this.userId = userId;
        this.resumeNum = resumeNum;
        this.jdNum = jdNum;
        this.companyName = companyName;
        this.positionTitle = positionTitle;
        this.overallLevel = overallLevel;
        this.redCount = redCount;
        this.yellowCount = yellowCount;
        this.greenCount = greenCount;
        this.retryCount = 0;
    }

    public void updateReanalysis(
            String companyName,
            String positionTitle,
            String overallLevel,
            int redCount,
            int yellowCount,
            int greenCount
    ) {
        // 재분석 결과로 분석 요약 정보를 갱신
        if (companyName != null && !companyName.isBlank()) {
            this.companyName = companyName;
        }
        if (positionTitle != null && !positionTitle.isBlank()) {
            this.positionTitle = positionTitle;
        }

        this.overallLevel = overallLevel;
        this.redCount = redCount;
        this.yellowCount = yellowCount;
        this.greenCount = greenCount;
        this.retryCount++;
    }

    @PrePersist
    void prePersist() {
        // 최초 저장 시 생성/수정/저장 시간을 같은 값으로 설정
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSavedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        // 수정될 때마다 수정/저장 시간을 갱신
        LocalDateTime now = LocalDateTime.now();
        this.updatedAt = now;
        this.lastSavedAt = now;
    }
}
