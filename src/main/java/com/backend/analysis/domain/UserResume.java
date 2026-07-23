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
@Table(name = "user_resume")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserResume extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Lob
    @Column(name = "resume_content", nullable = false, columnDefinition = "TEXT")
    private String resumeContent;

    @Column(name = "resume_file_name", length = 255)
    private String resumeFileName;

    @Column(name = "resume_file_size")
    private Long resumeFileSize;

    @Column(name = "last_saved_at")
    private LocalDateTime lastSavedAt;

    @Builder
    private UserResume(User user, String resumeContent, String resumeFileName, Long resumeFileSize) {
        this.user = user;
        this.resumeContent = resumeContent;
        this.resumeFileName = resumeFileName;
        this.resumeFileSize = resumeFileSize;
    }

    public void updateResumeContent(String resumeContent, LocalDateTime savedAt) {
        this.resumeContent = resumeContent;
        this.lastSavedAt = savedAt;
    }
}
