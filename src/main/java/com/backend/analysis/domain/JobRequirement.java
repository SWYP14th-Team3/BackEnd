package com.backend.analysis.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "job_requirement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRequirement {

    // 채용공고 요건 row의 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 분석 결과에 속한 요건인지 연결
    @Column(name = "analysis_result_id", nullable = false)
    private Long analysisResultId;

    // REQUIRED / WORK_SKILL / DOMAIN / PREFERRED
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Lob
    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    @Lob
    @Column(name = "source_text", columnDefinition = "LONGTEXT")
    private String sourceText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private JobRequirement(
            Long analysisResultId,
            String category,
            String title,
            String description,
            String sourceText
    ) {
        this.analysisResultId = analysisResultId;
        this.category = category;
        this.title = title;
        this.description = description;
        this.sourceText = sourceText;
    }

    public void updateReanalysis(
            String category,
            String title,
            String description,
            String sourceText
    ) {
        // 재분석 응답 기준으로 기존 요건 내용을 갱신
        this.category = category;
        this.title = title;
        this.description = description;
        this.sourceText = sourceText;
    }

    @PrePersist
    void prePersist() {
        // 요건이 처음 저장된 시간
        this.createdAt = LocalDateTime.now();
    }
}
