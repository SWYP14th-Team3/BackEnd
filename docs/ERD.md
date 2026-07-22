```mermaid
erDiagram
    USER {
        Long id PK
        String email "소셜 가입 이메일, nullable"
        String provider "GOOGLE / KAKAO"
        String provider_id "소셜 고유 ID"
        String name "사용자 이름"
        DateTime terms_agreed_at "서비스 이용약관 동의일"
        DateTime privacy_agreed_at "개인정보 수집·이용 및 AI 분석 제3자 제공 동의일"
        String terms_version "동의한 서비스 이용약관 버전"
        String privacy_version "동의한 개인정보처리방침 버전"
        DateTime created_at "가입일"
    }

    USER_RESUME {
        Long id PK
        Long user_id FK "USER.id"
        Text resume_content "PDF에서 추출된 이력서 텍스트 및 편집본"
        String resume_file_name "이력서 파일명"
        Long resume_file_size "이력서 파일 크기(byte)"

        DateTime created_at "생성일"
        DateTime updated_at "수정일"
        DateTime last_saved_at "이력서 자동저장 시각"
    }

    JOB_DESCRIPTION {
        Long id PK
        Long user_id FK "USER.id"

        String job_input_type "URL / TEXT / IMAGE"
        String job_url "공고 URL, URL 방식이 아니면 NULL"
        String job_platform "채용공고 플랫폼"
        String company_name "회사명"
        String position_title "포지션명"
        Text jd_original_text "공고 원문 텍스트, 가공 금지"
        Text jd_summary_text "공고 요약본, 마크다운"

        DateTime created_at "생성일"
        DateTime updated_at "수정일"
    }

    JOB_POSTING_IMAGE {
        Long id PK
        Long job_description_id FK "JOB_DESCRIPTION.id"
        String original_file_name "공고 이미지 파일명"
        String content_type "image/jpeg 또는 image/png"
        Long file_size "이미지 파일 크기(byte)"
        Integer image_order "업로드 순서, 최대 10장"
        DateTime created_at "생성일"
    }

    ANALYSIS_RESULT {
        Long id PK
        Long user_id FK "USER.id"
        Long resume_id FK "USER_RESUME.id"
        Long job_description_id FK "JOB_DESCRIPTION.id"

        String overall_level "HIGH / MEDIUM / LOW"
        Integer red_count "없음 개수"
        Integer yellow_count "보강 필요 개수"
        Integer green_count "확인됨 개수"

        String previous_overall_level "직전 재분석 전 등급"
        Integer previous_red_count "직전 재분석 전 없음 개수"
        Integer previous_yellow_count "직전 재분석 전 보강 필요 개수"
        Integer previous_green_count "직전 재분석 전 확인됨 개수"
        DateTime last_reanalyzed_at "마지막 재분석 완료 시각"

        Integer retry_count "성공한 재분석 횟수, 최대 5"
        String satisfaction "LIKE / DISLIKE / NULL"

        DateTime final_saved_at "저장하기 버튼으로 최종 저장한 시각"
        DateTime created_at "생성일"
        DateTime updated_at "수정일"
        DateTime deleted_at "삭제일, soft delete"
    }

    JOB_REQUIREMENT {
        Long id PK
        Long analysis_result_id FK "ANALYSIS_RESULT.id"

        String requirement_type "REQUIRED / PREFERRED"
        String category "자격요건 / 업무역량 / 도메인 / 우대사항"
        String title "공고 요건명"
        Text description "요건 설명"
        Text jd_evidence "공고에서 해당 요건을 판단한 원문 근거"
        Integer input_order "LLM이 추출한 원래 순서"

        DateTime created_at "생성일"
    }

    REQUIREMENT_EVALUATION {
        Long id PK
        Long requirement_id FK "JOB_REQUIREMENT.id"
        Long analysis_result_id FK "ANALYSIS_RESULT.id"

        String match_status "CONFIRMED / NEEDS_IMPROVEMENT / MISSING"
        String display_title "화면 노출용 제목"
        Text resume_evidence "이력서에서 확인된 근거"
        Text judge_reason "판정 근거 문장, 카드에 그대로 노출"
        Text feedback "상세 피드백"
        Text revision_suggestion "한끗 차이 문구 또는 수정 제안"
        Integer effect_score "영향력 점수, 1~5"
        Integer effort_score "수정 난이도 점수, 1~5"
        Decimal priority_score "우선순위 점수, effect_score^2 / effort_score"
        Integer sort_order "섹션 내 정렬 순서"

        DateTime updated_at "수정일"
    }

    USER ||--o{ USER_RESUME : "이력서 보유"
    USER ||--o{ JOB_DESCRIPTION : "채용공고 보유"
    USER ||--o{ ANALYSIS_RESULT : "분석 결과 보유"
    USER_RESUME ||--o{ ANALYSIS_RESULT : "분석에 사용된 이력서"
    JOB_DESCRIPTION ||--o{ ANALYSIS_RESULT : "분석에 사용된 채용공고"
    JOB_DESCRIPTION ||--o{ JOB_POSTING_IMAGE : "공고 이미지 보유"
    ANALYSIS_RESULT ||--o{ JOB_REQUIREMENT : "공고 요건 포함"
    ANALYSIS_RESULT ||--o{ REQUIREMENT_EVALUATION : "요건별 최신 평가 보유"
    JOB_REQUIREMENT ||--|| REQUIREMENT_EVALUATION : "요건별 최신 평가"
```

## v7 반영 요약

- 최초 가입 약관 동의를 위해 `USER`에 `terms_agreed_at`, `privacy_agreed_at`, 약관 버전 컬럼을 추가했습니다.
- 이력서 자동저장은 `USER_RESUME.last_saved_at`에서 관리하고, 최종 저장 버튼은 `ANALYSIS_RESULT.final_saved_at`에서 분리해 관리합니다.
- 공고 입력 방식이 `URL / TEXT / IMAGE`로 확장되므로 `JOB_DESCRIPTION.job_input_type`을 추가했습니다.
- 결과 화면의 원본/요약 탭을 위해 `JOB_DESCRIPTION`에 `jd_original_text`와 `jd_summary_text`를 분리했습니다.
- 공고 이미지 업로드 최대 10장 요구사항을 위해 `JOB_POSTING_IMAGE` 테이블을 추가했습니다.
- 필수/우대 섹션 분리를 위해 `JOB_REQUIREMENT.requirement_type`을 추가했습니다.
- LLM2 판정 근거 노출을 위해 `JOB_REQUIREMENT.jd_evidence`, `REQUIREMENT_EVALUATION.resume_evidence`, `judge_reason`을 분리했습니다.
- LLM3 우선순위 정렬을 위해 `effect_score`, `effort_score`, `priority_score`, `sort_order`를 `REQUIREMENT_EVALUATION`에 추가했습니다.
- 재분석은 공고 요건을 고정하고 최신 평가만 갱신하는 구조로 보고, `JOB_REQUIREMENT`는 고정 요건, `REQUIREMENT_EVALUATION`은 최신 매칭 결과로 분리했습니다.
- 최근 재분석 변화 배너를 위해 `ANALYSIS_RESULT`에 `previous_overall_level`, `previous_red_count`, `previous_yellow_count`, `previous_green_count`, `last_reanalyzed_at`을 추가했습니다.
- 회원 탈퇴는 `USER` 기준 관련 데이터 삭제가 필요하므로 FK cascade 또는 서비스 레벨 일괄 삭제 정책이 필요합니다.

## 재분석 변화 배너 저장 방식

- 변화 배너는 전체 재분석 이력이 아니라 가장 최근 재분석 1회만 보여주는 전제로 설계했습니다.
- 재분석 성공 직전에 현재 `overall_level`, `red_count`, `yellow_count`, `green_count`를 `previous_*` 컬럼에 복사합니다.
- 새 재분석 결과를 현재 `overall_level`, `red_count`, `yellow_count`, `green_count`에 저장합니다.
- `retry_count`를 1 증가시키고 `last_reanalyzed_at`을 갱신합니다.
- API 응답은 `previous_*`와 현재 값을 함께 내려주면 프론트에서 `등급 상승`, `미충족 2→0`, `보강 필요 3→1`, `충족 5→9` 같은 문구를 만들 수 있습니다.
- 과거 재분석 이력 전체가 필요해지면 `ANALYSIS_REANALYSIS_HISTORY` 같은 별도 히스토리 테이블을 추가하는 방향으로 확장합니다.
