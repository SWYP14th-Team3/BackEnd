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
@Table(name = "user_resume")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserResume {

    // 이력서 요약 row의 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 이력서를 업로드한 사용자 ID
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Gemini가 PDF에서 추출해 정리한 이력서 내용
    @Lob
    @Column(name = "resume_content", nullable = false, columnDefinition = "LONGTEXT")
    private String resumeContent;

    @Column(name = "resume_file_name", length = 255)
    private String resumeFileName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_saved_at", nullable = false)
    private LocalDateTime lastSavedAt;

    @Builder
    private UserResume(Long userId, String resumeContent, String resumeFileName) {
        // 이력서 마크다운 정리 결과 저장
        this.userId = userId;
        this.resumeContent = resumeContent;
        this.resumeFileName = resumeFileName;
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
        // 이력서 내용이 수정되면 저장 시간 갱신
        LocalDateTime now = LocalDateTime.now();
        this.updatedAt = now;
        this.lastSavedAt = now;
    }
}
