package com.backend.analysis.domain;

import com.backend.global.common.entity.BaseTimeEntity;
import com.backend.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "job_input_type", nullable = false, length = 20)
    private JobInputType jobInputType;

    @Column(name = "job_url", length = 1000)
    private String jobUrl;

    @Column(name = "job_platform", length = 100)
    private String jobPlatform;

    @Column(name = "company_name", length = 100)
    private String companyName;

    @Column(name = "position_title", length = 100)
    private String positionTitle;

    @Lob
    @Column(name = "jd_original_text", nullable = false, columnDefinition = "TEXT")
    private String jdOriginalText;

    @Lob
    @Column(name = "jd_summary_text", nullable = false, columnDefinition = "TEXT")
    private String jdSummaryText;

    @Builder
    private JobDescription(
            User user,
            JobInputType jobInputType,
            String jobUrl,
            String companyName,
            String positionTitle,
            String jobPlatform,
            String jdOriginalText,
            String jdSummaryText,
            String jdContent
    ) {
        this.user = user;
        this.jobInputType = jobInputType != null ? jobInputType : JobInputType.TEXT;
        this.jobUrl = jobUrl;
        this.companyName = companyName;
        this.positionTitle = positionTitle;
        this.jobPlatform = jobPlatform;
        this.jdOriginalText = jdOriginalText != null ? jdOriginalText : jdContent;
        this.jdSummaryText = jdSummaryText != null ? jdSummaryText : jdContent;
    }

    public String getJdContent() {
        return jdSummaryText;
    }
}
