-- v7 분석 결과 상세 조회 화면 확인용 더미 데이터
-- 사용 방법:
-- 1. 특정 계정에 넣으려면 @target_user_id에 users.id를 지정합니다.
-- 2. id를 모르면 @target_email에 로그인 계정 이메일을 지정합니다.
-- 3. 둘 다 NULL이면 users 테이블의 최신 계정에 붙습니다.

START TRANSACTION;

SET @target_user_id := NULL;
SET @target_email := 'user@gmail.com';

SET @user_id := COALESCE(
    @target_user_id,
    (
        SELECT id
        FROM users
        WHERE email = (CONVERT(@target_email USING utf8mb4) COLLATE utf8mb4_unicode_ci)
        ORDER BY id DESC
        LIMIT 1
    ),
    (SELECT id FROM users ORDER BY id DESC LIMIT 1)
);

INSERT INTO user_resume (
    user_id,
    resume_content,
    resume_file_name,
    resume_file_size,
    created_at,
    updated_at,
    last_saved_at
) VALUES (
    @user_id,
    '강인성

백엔드 개발자

요약
- Spring Boot 기반 REST API 개발 경험
- JWT 인증/인가, MySQL, Redis 사용 경험
- Docker Compose 기반 로컬/운영 환경 구성 경험

프로젝트
1. ResuFit 백엔드
- Spring Boot로 OAuth 로그인, JWT 재발급, 분석 결과 API 구현
- MySQL JPA 엔티티 설계 및 Redis refresh token 저장소 구현
- Docker Compose 기반 MySQL/Redis 개발 환경 구성

2. 이력서 분석 서비스
- PDF 업로드 검증 및 텍스트 추출 로직 구현
- 채용공고 URL 크롤링 결과를 LLM 분석 프롬프트에 연결
- 분석 결과 목록, 삭제, 만족도 저장 API 구현

기술
- Java, Spring Boot, Spring Security, JPA, MySQL, Redis, Docker, GitHub Actions',
    'kang-resume.pdf',
    480029,
    NOW(),
    NOW(),
    DATE_SUB(NOW(), INTERVAL 10 MINUTE)
);

SET @resume_id := LAST_INSERT_ID();

INSERT INTO job_description (
    user_id,
    job_input_type,
    job_url,
    job_platform,
    company_name,
    position_title,
    jd_original_text,
    jd_summary_text,
    created_at,
    updated_at
) VALUES (
    @user_id,
    'URL',
    'https://company.com/jobs/123',
    'company',
    '카카오',
    '백엔드 개발자',
    '카카오 백엔드 개발자 채용 공고

주요업무
- Spring Boot 기반 서버 API 개발
- 대용량 트래픽을 고려한 서비스 설계 및 운영
- MySQL, Redis 기반 데이터 저장소 설계와 성능 개선
- Docker, Kubernetes 기반 배포 환경 운영

자격요건
- Java 또는 Kotlin 기반 백엔드 개발 경험 2년 이상
- Spring Boot 기반 REST API 개발 경험
- MySQL 등 RDBMS 설계 및 쿼리 최적화 경험
- Git을 활용한 협업 경험

우대사항
- AWS 환경에서 서비스 배포 및 운영 경험
- Redis, Kafka 등 메시징/캐시 시스템 경험
- CI/CD 파이프라인 구축 경험
- 테스트 코드 작성과 코드 리뷰 문화에 익숙한 분',
    '## 채용공고 요약
- Spring Boot 기반 백엔드 API 개발
- MySQL, Redis 기반 저장소 설계 및 운영
- Docker/Kubernetes 기반 배포 환경 경험 우대
- AWS, CI/CD, 테스트 코드 작성 경험 우대',
    NOW(),
    NOW()
);

SET @job_description_id := LAST_INSERT_ID();

INSERT INTO analysis_result (
    user_id,
    resume_id,
    job_description_id,
    overall_level,
    red_count,
    yellow_count,
    green_count,
    previous_overall_level,
    previous_red_count,
    previous_yellow_count,
    previous_green_count,
    last_reanalyzed_at,
    retry_count,
    satisfaction,
    final_saved_at,
    created_at,
    updated_at,
    deleted_at
) VALUES (
    @user_id,
    @resume_id,
    @job_description_id,
    'MEDIUM',
    1,
    2,
    3,
    'LOW',
    3,
    2,
    1,
    DATE_SUB(NOW(), INTERVAL 5 MINUTE),
    1,
    'LIKE',
    DATE_SUB(NOW(), INTERVAL 3 MINUTE),
    NOW(),
    NOW(),
    NULL
);

SET @analysis_result_id := LAST_INSERT_ID();

INSERT INTO job_requirement (
    analysis_result_id,
    requirement_type,
    category,
    title,
    description,
    jd_evidence,
    input_order,
    created_at
) VALUES
(@analysis_result_id, 'REQUIRED', 'QUALIFICATION', 'Spring Boot 개발 경험', 'Spring Boot 기반 REST API 개발 경험이 필요합니다.', 'Spring Boot 기반 REST API 개발 경험', 1, NOW()),
(@analysis_result_id, 'REQUIRED', 'WORK_COMPETENCY', 'MySQL 설계 및 쿼리 최적화 경험', 'RDBMS 설계와 성능 개선 경험이 필요합니다.', 'MySQL 등 RDBMS 설계 및 쿼리 최적화 경험', 2, NOW()),
(@analysis_result_id, 'REQUIRED', 'WORK_COMPETENCY', '대용량 트래픽 서비스 운영 경험', '트래픽을 고려한 서비스 설계와 운영 경험이 필요합니다.', '대용량 트래픽을 고려한 서비스 설계 및 운영', 3, NOW()),
(@analysis_result_id, 'PREFERRED', 'PREFERENCE', 'AWS 배포 경험', 'AWS 환경에서 서비스를 배포하고 운영한 경험을 우대합니다.', 'AWS 환경에서 서비스 배포 및 운영 경험', 4, NOW()),
(@analysis_result_id, 'PREFERRED', 'PREFERENCE', 'CI/CD 파이프라인 구축 경험', '배포 자동화와 테스트 자동화 경험을 우대합니다.', 'CI/CD 파이프라인 구축 경험', 5, NOW());

SET @req_spring := (SELECT id FROM job_requirement WHERE analysis_result_id = @analysis_result_id AND input_order = 1);
SET @req_mysql := (SELECT id FROM job_requirement WHERE analysis_result_id = @analysis_result_id AND input_order = 2);
SET @req_traffic := (SELECT id FROM job_requirement WHERE analysis_result_id = @analysis_result_id AND input_order = 3);
SET @req_aws := (SELECT id FROM job_requirement WHERE analysis_result_id = @analysis_result_id AND input_order = 4);
SET @req_cicd := (SELECT id FROM job_requirement WHERE analysis_result_id = @analysis_result_id AND input_order = 5);

INSERT INTO requirement_evaluation (
    requirement_id,
    analysis_result_id,
    match_status,
    display_title,
    resume_evidence,
    judge_reason,
    feedback,
    revision_suggestion,
    effect_score,
    effort_score,
    priority_score,
    sort_order,
    updated_at
) VALUES
(@req_spring, @analysis_result_id, 'CONFIRMED', 'Spring Boot 경험이 확인됐어요', 'Spring Boot로 OAuth 로그인, JWT 재발급, 분석 결과 API를 구현한 경험이 확인됩니다.', '공고의 Spring Boot REST API 개발 요구와 이력서의 API 구현 경험이 직접적으로 일치합니다.', '이미 갖춰진 경험입니다. API 설계 범위와 운영 환경에서 검증한 내용을 조금 더 앞쪽에 배치하면 좋습니다.', 'Spring Boot 기반 OAuth/JWT 인증 API와 분석 결과 API를 설계부터 구현, 테스트까지 담당했다고 강조해보세요.', NULL, NULL, NULL, 3, NOW()),
(@req_mysql, @analysis_result_id, 'NEEDS_IMPROVEMENT', 'MySQL 설계 경험을 더 구체화하세요', 'MySQL과 JPA 엔티티 설계 경험은 확인되지만 쿼리 최적화나 인덱스 개선 근거는 부족합니다.', 'RDBMS 사용 경험은 있으나 공고가 요구하는 설계 및 최적화 역량을 충분히 입증하지 못합니다.', '테이블 설계, 인덱스, 조회 성능 개선 같은 구체적인 사례를 추가하면 보강됩니다.', '분석 결과 목록 조회에서 페이지네이션과 회사명 검색 쿼리를 설계하고, 필요한 인덱스 기준을 검토했다고 작성해보세요.', 4, 2, 8.0000, 1, NOW()),
(@req_traffic, @analysis_result_id, 'MISSING', '대용량 트래픽 운영 근거가 부족해요', '이력서에서 대용량 트래픽을 직접 운영하거나 장애를 개선한 경험은 확인되지 않습니다.', '공고의 대용량 트래픽 설계 및 운영 요구에 대응하는 명확한 이력서 근거가 없습니다.', '실제 트래픽 수치가 없다면 부하 테스트, 병목 분석, 캐시 적용처럼 근접한 경험을 추가하는 방향이 좋습니다.', '동시 요청을 고려한 API 응답 최적화, Redis 캐시 적용 검토, 부하 테스트 경험이 있다면 수치와 함께 추가해보세요.', 5, 3, 8.3333, 1, NOW()),
(@req_aws, @analysis_result_id, 'CONFIRMED', 'AWS 배포 경험이 확인됐어요', 'AWS EC2와 Docker를 이용한 Spring Boot 서버 배포 경험이 확인됩니다.', '공고의 AWS 배포 우대사항과 이력서의 배포 경험이 일치합니다.', '배포 경험과 함께 HTTPS, 모니터링, 롤백 전략이 있다면 강점이 더 분명해집니다.', 'AWS EC2 배포와 함께 Docker Compose 운영, Nginx HTTPS 적용 경험을 강조해보세요.', NULL, NULL, NULL, 2, NOW()),
(@req_cicd, @analysis_result_id, 'NEEDS_IMPROVEMENT', 'CI/CD 경험을 결과 중심으로 보강하세요', 'GitHub Actions 언급은 있으나 실제 파이프라인 구성 범위와 자동화 결과가 구체적이지 않습니다.', 'CI/CD 경험은 일부 확인되지만 공고의 우대사항으로 보기에는 구체성이 부족합니다.', '빌드, 테스트, 배포 자동화 중 어떤 단계를 담당했는지 명확히 쓰는 것이 좋습니다.', 'GitHub Actions로 테스트 실행과 Docker 이미지 빌드, 서버 배포를 자동화한 흐름을 단계별로 작성해보세요.', 3, 2, 4.5000, 2, NOW());

SELECT
    @user_id AS user_id,
    @resume_id AS resume_id,
    @job_description_id AS job_description_id,
    @analysis_result_id AS analysis_result_id;

COMMIT;
