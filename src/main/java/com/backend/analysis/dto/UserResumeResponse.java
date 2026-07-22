package com.backend.analysis.dto;

import com.backend.analysis.domain.UserResume;
import java.time.LocalDateTime;

// 저장된 이력서 내용을 프론트에 보여주기 위한 응답
public record UserResumeResponse(
        Long id,
        Long userId,
        String resumeContent,
        String resumeFileName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastSavedAt
) {

    public static UserResumeResponse from(UserResume resume) {
        // UserResume Entity를 응답 DTO로 변환
        return new UserResumeResponse(
                resume.getId(),
                resume.getUserId(),
                resume.getResumeContent(),
                resume.getResumeFileName(),
                resume.getCreatedAt(),
                resume.getUpdatedAt(),
                resume.getLastSavedAt()
        );
    }
}
