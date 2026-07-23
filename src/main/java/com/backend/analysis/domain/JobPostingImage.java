package com.backend.analysis.domain;

import com.backend.global.common.entity.BaseCreatedTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "job_posting_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPostingImage extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_description_id", nullable = false)
    private JobDescription jobDescription;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "image_order", nullable = false)
    private Integer imageOrder;

    @Builder
    private JobPostingImage(
            JobDescription jobDescription,
            String originalFileName,
            String contentType,
            Long fileSize,
            Integer imageOrder
    ) {
        this.jobDescription = jobDescription;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.imageOrder = imageOrder;
    }
}
