package com.backend.analysis.infrastructure;

import com.backend.global.config.JpaAuditingConfig;
import org.springframework.context.annotation.Import;
import com.backend.analysis.domain.AnalysisResult;
import com.backend.analysis.domain.JobDescription;
import com.backend.analysis.domain.JobRequirement;
import com.backend.analysis.domain.MatchStatus;
import com.backend.analysis.domain.OverallLevel;
import com.backend.analysis.domain.RequirementCategory;
import com.backend.analysis.domain.RequirementEvaluation;
import com.backend.analysis.domain.UserResume;
import com.backend.user.domain.Provider;
import com.backend.user.domain.User;
import com.backend.user.infrastructure.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class AnalysisRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private UserResumeRepository userResumeRepository;

    @Autowired
    private JobDescriptionRepository jobDescriptionRepository;

    @Autowired
    private JobRequirementRepository jobRequirementRepository;

    @Autowired
    private RequirementEvaluationRepository requirementEvaluationRepository;

    @Test
    @DisplayName("유저, 분석 결과, 공고 요건, 요건별 평가 결과를 저장하고 조회할 수 있다")
    void saveAndFindAnalysisData() {
        // given
        String unique = String.valueOf(System.currentTimeMillis());

        User user = User.createSocialUser(
                "test" + unique + "@example.com",
                Provider.GOOGLE,
                "google-" + unique,
                "테스트유저"
        );

        User savedUser = userRepository.save(user);

        UserResume userResume = UserResume.builder()
                .user(savedUser)
                .resumeContent("현재 편집 중인 이력서 텍스트")
                .resumeFileName("resume.md")
                .build();

        UserResume savedUserResume = userResumeRepository.save(userResume);

        JobDescription jobDescription = JobDescription.builder()
                .user(savedUser)
                .companyName("테스트회사")
                .positionTitle("백엔드 개발자")
                .jobPlatform("직접 입력")
                .jdContent("백엔드 개발자 채용 공고 원문")
                .build();

        JobDescription savedJobDescription = jobDescriptionRepository.save(jobDescription);

        AnalysisResult analysisResult = AnalysisResult.builder()
                .user(savedUser)
                .userResume(savedUserResume)
                .jobDescription(savedJobDescription)
                .overallLevel(OverallLevel.MEDIUM)
                .redCount(1)
                .yellowCount(2)
                .greenCount(3)
                .build();

        AnalysisResult savedAnalysisResult = analysisResultRepository.save(analysisResult);

        JobRequirement jobRequirement = JobRequirement.builder()
                .analysisResult(savedAnalysisResult)
                .category(RequirementCategory.WORK_COMPETENCY)
                .title("Spring Boot 개발 경험")
                .description("Spring Boot 기반 백엔드 개발 경험이 필요합니다.")
                .sourceText("공고 원문 중 Spring Boot 관련 문장")
                .build();

        JobRequirement savedJobRequirement = jobRequirementRepository.save(jobRequirement);

        RequirementEvaluation evaluation = RequirementEvaluation.builder()
                .jobRequirement(savedJobRequirement)
                .matchStatus(MatchStatus.NEEDS_IMPROVEMENT)
                .resumeEvidence("이력서에 Spring 프로젝트 경험이 일부 존재함")
                .feedback("Spring Boot 경험은 확인되지만 구체적인 성과가 부족합니다.")
                .revisionSuggestion("프로젝트에서 담당한 API 구현 내용과 성과를 추가하세요.")
                .build();

        requirementEvaluationRepository.save(evaluation);

        // when
        List<AnalysisResult> analysisResults =
                analysisResultRepository.findAllByUserAndDeletedAtIsNullOrderByCreatedAtDesc(savedUser);

        List<JobRequirement> requirements =
                jobRequirementRepository.findAllByAnalysisResultOrderByIdAsc(savedAnalysisResult);

        RequirementEvaluation foundEvaluation =
                requirementEvaluationRepository.findByJobRequirement(savedJobRequirement)
                        .orElseThrow();

        // then
        assertThat(analysisResults).hasSize(1);
        assertThat(analysisResults.get(0).getJobDescription().getCompanyName()).isEqualTo("테스트회사");
        assertThat(analysisResults.get(0).getUserResume().getResumeContent()).isEqualTo("현재 편집 중인 이력서 텍스트");
        assertThat(analysisResults.get(0).getRetryCount()).isEqualTo(0);
        assertThat(analysisResults.get(0).getSatisfaction()).isNull();

        assertThat(requirements).hasSize(1);
        assertThat(requirements.get(0).getTitle()).isEqualTo("Spring Boot 개발 경험");

        assertThat(foundEvaluation.getMatchStatus()).isEqualTo(MatchStatus.NEEDS_IMPROVEMENT);
        assertThat(foundEvaluation.getFeedback()).contains("구체적인 성과가 부족");
    }

    @Test
    @DisplayName("삭제 처리된 분석 결과는 목록 조회에서 제외된다")
    void findAllByUserExcludesDeletedAnalysisResult() {
        // given
        String unique = String.valueOf(System.currentTimeMillis());

        User user = User.createSocialUser(
                "deleted" + unique + "@example.com",
                Provider.GOOGLE,
                "google-deleted-" + unique,
                "삭제테스트유저"
        );

        User savedUser = userRepository.save(user);

        UserResume userResume = UserResume.builder()
                .user(savedUser)
                .resumeContent("현재 편집 중인 이력서 텍스트")
                .resumeFileName("resume.md")
                .build();

        UserResume savedUserResume = userResumeRepository.save(userResume);

        JobDescription jobDescription = JobDescription.builder()
                .user(savedUser)
                .companyName("삭제테스트회사")
                .positionTitle("백엔드 개발자")
                .jobPlatform("직접 입력")
                .jdContent("백엔드 개발자 채용 공고 원문")
                .build();

        JobDescription savedJobDescription = jobDescriptionRepository.save(jobDescription);

        AnalysisResult analysisResult = AnalysisResult.builder()
                .user(savedUser)
                .userResume(savedUserResume)
                .jobDescription(savedJobDescription)
                .overallLevel(OverallLevel.MEDIUM)
                .redCount(1)
                .yellowCount(2)
                .greenCount(3)
                .build();

        AnalysisResult savedAnalysisResult = analysisResultRepository.save(analysisResult);
        savedAnalysisResult.delete(LocalDateTime.now());
        analysisResultRepository.flush();

        // when
        List<AnalysisResult> analysisResults =
                analysisResultRepository.findAllByUserAndDeletedAtIsNullOrderByCreatedAtDesc(savedUser);

        // then
        assertThat(analysisResults).isEmpty();
        assertThat(savedAnalysisResult.getDeletedAt()).isNotNull();
    }
}
