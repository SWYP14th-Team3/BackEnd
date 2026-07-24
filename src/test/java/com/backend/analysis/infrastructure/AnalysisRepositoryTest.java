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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    @Autowired
    private EntityManager entityManager;

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

    @Test
    @DisplayName("로그인 사용자의 분석 결과 목록을 페이지네이션으로 조회하고 삭제된 결과는 제외한다")
    void findAnalysisResultsWithPagination() {
        // given
        String unique = String.valueOf(System.currentTimeMillis());

        User user = userRepository.save(User.createSocialUser(
                "list" + unique + "@example.com",
                Provider.GOOGLE,
                "google-list-" + unique,
                "목록테스트유저"
        ));
        User otherUser = userRepository.save(User.createSocialUser(
                "other-list" + unique + "@example.com",
                Provider.KAKAO,
                "kakao-list-" + unique,
                "다른유저"
        ));

        AnalysisResult kakao = saveAnalysisResult(user, "카카오", "백엔드 개발자");
        AnalysisResult naver = saveAnalysisResult(user, "네이버", "서버 개발자");
        saveAnalysisResult(otherUser, "라인", "백엔드 개발자");

        AnalysisResult deleted = saveAnalysisResult(user, "삭제회사", "백엔드 개발자");
        deleted.delete(LocalDateTime.now());
        analysisResultRepository.flush();

        // when
        Page<AnalysisResult> results = analysisResultRepository.findAllByUserAndDeletedAtIsNullOrderByCreatedAtDesc(
                user,
                PageRequest.of(0, 10)
        );

        // then
        assertThat(results.getTotalElements()).isEqualTo(2);
        assertThat(results.getContent())
                .extracting(AnalysisResult::getId)
                .containsExactlyInAnyOrder(kakao.getId(), naver.getId());
    }

    @Test
    @DisplayName("회사명이 있으면 로그인 사용자의 분석 결과 목록을 회사명으로 필터링한다")
    void findAnalysisResultsByCompanyNameWithPagination() {
        // given
        String unique = String.valueOf(System.currentTimeMillis());

        User user = userRepository.save(User.createSocialUser(
                "search" + unique + "@example.com",
                Provider.GOOGLE,
                "google-search-" + unique,
                "검색테스트유저"
        ));
        User otherUser = userRepository.save(User.createSocialUser(
                "other-search" + unique + "@example.com",
                Provider.KAKAO,
                "kakao-search-" + unique,
                "다른유저"
        ));

        AnalysisResult kakaoBank = saveAnalysisResult(user, "카카오뱅크", "백엔드 개발자");
        AnalysisResult kakaoPay = saveAnalysisResult(user, "카카오페이", "서버 개발자");
        saveAnalysisResult(user, "네이버", "백엔드 개발자");
        saveAnalysisResult(otherUser, "카카오엔터프라이즈", "백엔드 개발자");

        AnalysisResult deletedKakao = saveAnalysisResult(user, "카카오스타일", "백엔드 개발자");
        deletedKakao.delete(LocalDateTime.now());
        analysisResultRepository.flush();

        // when
        Page<AnalysisResult> results = analysisResultRepository
                .findAllByUserAndDeletedAtIsNullAndJobDescription_CompanyNameContainingIgnoreCaseOrderByCreatedAtDesc(
                        user,
                        "카카오",
                        PageRequest.of(0, 10)
                );

        // then
        assertThat(results.getTotalElements()).isEqualTo(2);
        assertThat(results.getContent())
                .extracting(AnalysisResult::getId)
                .containsExactlyInAnyOrder(kakaoBank.getId(), kakaoPay.getId());
        assertThat(results.getContent())
                .extracting(result -> result.getJobDescription().getCompanyName())
                .containsExactlyInAnyOrder("카카오뱅크", "카카오페이");
    }

    @Test
    @DisplayName("회원 탈퇴 시 로그인 사용자의 분석 관련 데이터를 모두 hard delete 할 수 있다")
    void deleteAllAnalysisDataByUserIdForWithdrawal() {
        // given
        String unique = String.valueOf(System.currentTimeMillis());

        User user = userRepository.save(User.createSocialUser(
                "withdrawal" + unique + "@example.com",
                Provider.GOOGLE,
                "google-withdrawal-" + unique,
                "탈퇴테스트유저"
        ));
        User otherUser = userRepository.save(User.createSocialUser(
                "other-withdrawal" + unique + "@example.com",
                Provider.KAKAO,
                "kakao-withdrawal-" + unique,
                "다른유저"
        ));

        AnalysisResult analysisResult = saveAnalysisResult(user, "탈퇴회사", "백엔드 개발자");
        AnalysisResult otherAnalysisResult = saveAnalysisResult(otherUser, "유지회사", "서버 개발자");

        JobRequirement requirement = jobRequirementRepository.save(JobRequirement.builder()
                .analysisResult(analysisResult)
                .category(RequirementCategory.WORK_COMPETENCY)
                .title("Spring Boot 개발 경험")
                .description("Spring Boot 개발 경험이 필요합니다.")
                .sourceText("Spring Boot 개발 경험")
                .build());
        jobRequirementRepository.save(JobRequirement.builder()
                .analysisResult(otherAnalysisResult)
                .category(RequirementCategory.WORK_COMPETENCY)
                .title("JPA 개발 경험")
                .description("JPA 개발 경험이 필요합니다.")
                .sourceText("JPA 개발 경험")
                .build());

        requirementEvaluationRepository.save(RequirementEvaluation.builder()
                .jobRequirement(requirement)
                .matchStatus(MatchStatus.NEEDS_IMPROVEMENT)
                .resumeEvidence("Spring Boot 경험 일부 확인")
                .feedback("구체적인 역할이 부족합니다.")
                .revisionSuggestion("구현한 API를 구체적으로 작성하세요.")
                .build());

        Long userId = user.getId();
        Long otherUserId = otherUser.getId();
        entityManager.flush();
        entityManager.clear();

        // when
        requirementEvaluationRepository.deleteAllByUserId(userId);
        jobRequirementRepository.deleteAllByUserId(userId);
        analysisResultRepository.deleteAllByUserId(userId);
        userResumeRepository.deleteAllByUserId(userId);
        jobDescriptionRepository.deleteAllByUserId(userId);
        userRepository.deleteById(userId);
        userRepository.flush();
        entityManager.clear();

        // then
        assertThat(userRepository.existsById(userId)).isFalse();
        assertThat(analysisResultRepository.findAll())
                .extracting(result -> result.getUser().getId())
                .containsExactly(otherUserId);
        assertThat(jobRequirementRepository.findAll()).hasSize(1);
        assertThat(userResumeRepository.findAll()).hasSize(1);
        assertThat(jobDescriptionRepository.findAll()).hasSize(1);
    }

    private AnalysisResult saveAnalysisResult(User user, String companyName, String positionTitle) {
        UserResume resume = userResumeRepository.save(UserResume.builder()
                .user(user)
                .resumeContent("이력서 내용")
                .resumeFileName("resume.md")
                .build());

        JobDescription jobDescription = jobDescriptionRepository.save(JobDescription.builder()
                .user(user)
                .companyName(companyName)
                .positionTitle(positionTitle)
                .jobPlatform("직접 입력")
                .jdContent("채용공고 내용")
                .build());

        return analysisResultRepository.save(AnalysisResult.builder()
                .user(user)
                .userResume(resume)
                .jobDescription(jobDescription)
                .overallLevel(OverallLevel.MEDIUM)
                .redCount(1)
                .yellowCount(2)
                .greenCount(3)
                .build());
    }
}
