package com.backend.analysis.dto;

import com.backend.analysis.domain.JobDescription;
import java.time.LocalDateTime;

// 저장된 채용공고 내용을 프론트에 보여주기 위한 응답
public record JobDescriptionResponse(
        Long id,
        Long userId,
        String companyName,
        String positionTitle,
        String jobPlatform,
        String jdContent,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastSavedAt
) {

    public static JobDescriptionResponse from(JobDescription jobDescription) {
        // JobDescription Entity를 응답 DTO로 변환
        return new JobDescriptionResponse(
                jobDescription.getId(),
                jobDescription.getUserId(),
                jobDescription.getCompanyName(),
                jobDescription.getPositionTitle(),
                jobDescription.getJobPlatform(),
                jobDescription.getJdContent(),
                jobDescription.getCreatedAt(),
                jobDescription.getUpdatedAt(),
                jobDescription.getLastSavedAt()
        );
    }
}
