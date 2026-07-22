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
@Table(name = "job_description")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobDescription {

    // 채용공고 요약 row의 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 채용공고를 등록한 사용자 ID
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "position_title", length = 255)
    private String positionTitle;

    @Column(name = "job_platform", length = 100)
    private String jobPlatform;

    // Gemini가 URL/이미지/텍스트를 읽고 정리한 채용공고 내용
    @Lob
    @Column(name = "jd_content", nullable = false, columnDefinition = "LONGTEXT")
    private String jdContent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_saved_at", nullable = false)
    private LocalDateTime lastSavedAt;

    @Builder
    private JobDescription(
            Long userId,
            String companyName,
            String positionTitle,
            String jobPlatform,
            String jdContent
    ) {
        // 채용공고 마크다운 정리 결과 저장
        this.userId = userId;
        this.companyName = companyName;
        this.positionTitle = positionTitle;
        this.jobPlatform = jobPlatform;
        this.jdContent = jdContent;
    }

    @PrePersist
    void prePersist() {
        // 최초 저장 시 시간 컬럼 초기화
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastSavedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        // 채용공고 내용이 수정되면 저장 시간 갱신
        LocalDateTime now = LocalDateTime.now();
        this.updatedAt = now;
        this.lastSavedAt = now;
    }
}
