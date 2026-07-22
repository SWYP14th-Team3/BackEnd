USE resume;

-- 이력서 편집본 자동저장 API 테스트용 데이터입니다.
-- 실행 예:
-- docker exec -i resufit-mysql-local mysql -uroot -p1234 < sql/test-data/analysis-autosave-dummy.sql
--
-- 기존 users 데이터가 있으면 가장 작은 users.id를 사용합니다.
-- users 데이터가 없으면 테스트용 KAKAO 유저를 생성합니다.

SET @existing_user_id := (
    SELECT id
    FROM users
    ORDER BY id
    LIMIT 1
);

INSERT INTO users (
    created_at,
    name,
    email,
    provider_id,
    provider
)
SELECT
    NOW(6),
    '테스트사용자',
    'test-autosave@example.com',
    'test-autosave-provider',
    'KAKAO'
WHERE @existing_user_id IS NULL;

SET @user_id := COALESCE(@existing_user_id, LAST_INSERT_ID());

INSERT INTO analysis_result (
    green_count,
    red_count,
    retry_count,
    yellow_count,
    created_at,
    updated_at,
    user_id,
    company_name,
    position_title,
    job_platform,
    job_input_type,
    job_posting_raw,
    overall_level,
    resume_original_text,
    resume_current_text
)
VALUES (
    3,
    1,
    0,
    2,
    NOW(6),
    NOW(6),
    @user_id,
    'Test Company',
    'Backend Developer',
    'manual',
    'TEXT',
    'We are looking for a backend developer with Spring Boot, REST API, MySQL, Redis, and AWS experience.',
    'MEDIUM',
    'Original resume text for backend developer autosave test.',
    'Current resume text before autosave.'
);

SELECT
    LAST_INSERT_ID() AS analysisResultId,
    @user_id AS userId;
