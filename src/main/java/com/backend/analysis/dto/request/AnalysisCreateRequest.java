package com.backend.analysis.dto.request;

import com.backend.analysis.domain.JobInputType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnalysisCreateRequest {

    @NotNull(message = "공고 입력 방식은 필수입니다.")
    private JobInputType jobInputType;

    private String jobUrl;

    private String jobPlatform;

    private String jobPostingRaw;

    @NotBlank(message = "이력서 원본 텍스트는 필수입니다.")
    private String resumeOriginalText;

    @NotBlank(message = "현재 이력서 텍스트는 필수입니다.")
    private String resumeCurrentText;

    private String companyName;

    private String positionTitle;

    @AssertTrue(message = "URL 입력 방식에서는 공고 URL이 필수입니다.")
    public boolean isValidUrlInput() {
        if (jobInputType != JobInputType.URL) {
            return true;
        }

        return jobUrl != null && !jobUrl.isBlank();
    }

    @AssertTrue(message = "텍스트 입력 방식에서는 공고 원문 텍스트가 필수입니다.")
    public boolean isValidTextInput() {
        if (jobInputType != JobInputType.TEXT) {
            return true;
        }

        return jobPostingRaw != null && !jobPostingRaw.isBlank();
    }
}