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
@Table(name = "job_description")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobDescription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "company_name", length = 100)
    private String companyName;

    @Column(name = "position_title", length = 100)
    private String positionTitle;

    @Column(name = "job_platform", length = 100)
    private String jobPlatform;

    @Lob
    @Column(name = "jd_content", nullable = false, columnDefinition = "TEXT")
    private String jdContent;

    @Column(name = "last_saved_at")
    private LocalDateTime lastSavedAt;

    @Builder
    private JobDescription(
            User user,
            String companyName,
            String positionTitle,
            String jobPlatform,
            String jdContent
    ) {
        this.user = user;
        this.companyName = companyName;
        this.positionTitle = positionTitle;
        this.jobPlatform = jobPlatform;
        this.jdContent = jdContent;
    }
}
