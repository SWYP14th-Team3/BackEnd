# Gemini 이력서-채용공고 분석 API 작업 정리

이 문서는 PDF 이력서와 채용공고를 Gemini API로 비교 분석하는 기능의 현재 작업 내용을 정리한 문서입니다.

## 1. 기능 목표

사용자가 프론트엔드에서 아래 값을 업로드하면 백엔드가 Gemini API를 호출해서 이력서와 채용공고의 적합도를 분석합니다.

```text
PDF 이력서
채용공고 URL, 채용공고 텍스트, 또는 채용공고 이미지
JWT 인증 사용자
```

분석 결과는 아래 테이블에 저장됩니다.

```text
user_resume
job_description
job_posting_image
analysis_result
job_requirement
requirement_evaluation
```

## 2. 현재 API

현재 컨트롤러 위치:

```text
src/main/java/com/backend/analysis/controller/AnalysisController.java
```

엔드포인트:

```http
POST /api/analyses
Content-Type: multipart/form-data
```

요청 필드:

| 필드 | 필수 여부 | 설명 |
| --- | --- | --- |
| `resumeFile` | 필수 | PDF 이력서 파일입니다. |
| `jobInputType` | 필수 | `URL`, `TEXT`, `IMAGE` 중 하나입니다. |
| `jobUrl` | URL 방식일 때 필수 | 채용공고 URL입니다. |
| `jobText` | TEXT 방식일 때 필수 | 채용공고 원문 텍스트입니다. |
| `jobImages` | IMAGE 방식일 때 필수 | 채용공고 이미지 파일 목록입니다. 최대 10장입니다. |

사용자 식별은 요청 필드가 아니라 JWT 인증 정보의 `UserPrincipal`에서 가져옵니다.

재분석 엔드포인트:

```http
POST /api/analyses/{analysisResultId}/reanalyze
Content-Type: application/json
```

재분석 요청 본문:

```json
{
  "resumeCurrentText": "수정된 이력서 마크다운"
}
```

## 3. 전체 처리 흐름

```text
Frontend
  -> POST /api/analyses
  -> multipart/form-data 전송

AnalysisController
  -> 인증 사용자 ID 확인
  -> AnalysisService 호출

AnalysisService
  -> PDF 파일 검증
  -> jobInputType에 따라 URL/TEXT/IMAGE 입력 검증
  -> TEXT 모드면 jobText 사용
  -> URL 모드면 JobPostingCrawler로 채용공고 페이지 텍스트 추출
  -> IMAGE 모드면 Gemini에 이미지 전달
  -> user_resume, job_description, job_posting_image 저장
  -> Gemini 분석/우선순위/카드 문구 생성
  -> Gemini 응답을 DTO로 파싱
  -> analysis_result, job_requirement, requirement_evaluation 저장
  -> 응답 DTO 반환

GeminiAnalysisClient
  -> Gemini generateContent API 호출
  -> candidates[0].content.parts[0].text 추출
  -> JSON 문자열을 GeminiAnalysisResponse로 변환
```

## 4. Gemini 모델 변경

설정 파일:

```text
src/main/resources/application.yml
```

현재 설정:

```yaml
gemini:
  api-key: ${GEMINI_API_KEY}
  model: ${GEMINI_MODEL:gemini-3.1-flash-lite}
```

변경 이유:

```text
gemini-3.5-flash
  -> 직접 호출 테스트 결과 503 high demand 발생

gemini-2.5-flash
  -> 현재 API 키 기준 404 no longer available to new users 발생

gemini-2.0-flash 계열
  -> 현재 API 키 기준 429 quota exceeded 발생

gemini-3.1-flash-lite
  -> 직접 호출 테스트 결과 200 OK 반환
```

그래서 기본 모델을 `gemini-3.1-flash-lite`로 변경했습니다.

`.env.local`에 `GEMINI_MODEL`을 따로 지정하면 그 값이 우선 사용됩니다.

## 5. 프롬프트 구조 수정

수정 파일:

```text
src/main/java/com/backend/analysis/application/AnalysisService.java
```

기존 문제:

```json
{
  "analysisResult": {
    "companyName": "회사명",
    "positionTitle": "포지션명"
  },
  "requirements": [
    {
      "jobRequirement": {},
      "requirementEvaluation": {}
    }
  ]
}
```

위처럼 Gemini에게 중첩 JSON을 요구하고 있었지만, 실제 Java DTO는 아래처럼 최상위 필드를 기대합니다.

```java
public record GeminiAnalysisResponse(
        String companyName,
        String positionTitle,
        String overallLevel,
        String resumeOriginalText,
        List<GeminiRequirementResult> requirements
) {
}
```

현재 분석 프롬프트의 반환 JSON 형식은 아래 구조입니다.

```json
{
  "analyzable": true,
  "fail_side": null,
  "company": "회사명",
  "position": "포지션명",
  "requirements": [
    {
      "req_id": "r1",
      "content": "요건 원문",
      "importance": "필수 또는 우대",
      "status": "green 또는 yellow 또는 red",
      "jd_evidence": "공고 원문 근거",
      "resume_evidence": "이력서 원문 근거 또는 없음",
      "judge_reason": "판정 이유"
    }
  ]
}
```

## 6. 저장 방식

`AnalysisService`는 Gemini 응답을 받은 뒤 아래 순서로 저장합니다.

```text
1. analysis_result 저장
2. requirements 배열을 반복
3. 각 항목을 job_requirement에 저장
4. 각 항목의 평가 결과를 requirement_evaluation에 저장
5. 저장된 Entity를 AnalysisResponse로 변환해서 반환
```

상태값 개수는 서버에서 계산합니다.

```text
red -> redCount
yellow -> yellowCount
green -> greenCount
```

재분석은 새 테이블을 만들지 않고 기존 `analysis_result` row를 갱신합니다.

```text
1. 기존 job_requirement 요건 목록을 고정한다.
2. 수정된 resumeCurrentText와 기존 요건 목록으로 Gemini 재분석을 요청한다.
3. requirement_evaluation 최신 평가만 갱신한다.
4. analysis_result.previous_*에 직전 값을 복사한다.
5. analysis_result 현재 overall/red/yellow/green 값을 새 결과로 갱신한다.
6. retry_count를 1 증가시키고 last_reanalyzed_at을 갱신한다.
```

## 7. 테스트 요청

백엔드 실행:

```bash
cd /Users/banjaehyeon/Desktop/workspace/BackEnd
./gradlew bootRun
```

텍스트 모드 테스트:

```bash
curl -X POST http://localhost:8080/api/analyses \
  -H "Authorization: Bearer {accessToken}" \
  -F "resumeFile=@/path/to/resume.pdf;type=application/pdf" \
  -F "jobInputType=TEXT" \
  -F "jobText=Java Spring Boot 백엔드 개발자 채용. 필수요건은 Java, Spring Boot, MySQL 경험입니다. 우대사항은 Redis 경험입니다."
```

성공 응답 예시:

```json
{
  "data": {
    "analysisResultId": 2,
    "companyName": "해당없음",
    "positionTitle": "Java Spring Boot 백엔드 개발자",
    "overallLevel": "HIGH",
    "redCount": 0,
    "yellowCount": 1,
    "greenCount": 2,
    "retryCount": 0,
    "remainingRetryCount": 5,
    "jobInputType": "TEXT",
    "jobUrl": null,
    "jobPlatform": "TEXT",
    "resumeCurrentText": "...",
    "requirements": []
  },
  "message": "OK",
  "status": 200
}
```

실제 응답에서는 `requirements` 배열 안에 각 요건별 상세 진단 결과가 포함됩니다.

## 8. 검증 결과

최근 검증 결과:

```text
POST /api/analyses
HTTP/1.1 200
analysisResultId: 2
overallLevel: HIGH
DB 저장 확인 완료
```

DB 확인 쿼리:

```sql
select
    id,
    user_id,
    job_input_type,
    job_platform,
    company_name,
    position_title,
    overall_level,
    red_count,
    yellow_count,
    green_count
from analysis_result
order by id desc
limit 3;
```

확인된 저장 값:

```text
analysis_result.id: 2
analysis_result.user_id: JWT 인증 사용자 ID
job_description.job_input_type: TEXT
job_description.job_platform: TEXT
job_description.company_name: 해당없음
job_description.position_title: Java Spring Boot 백엔드 개발자
analysis_result.overall_level: HIGH
analysis_result.red_count: 0
analysis_result.yellow_count: 1
analysis_result.green_count: 2
```

## 9. 주의사항

`application.yml`에서 Hibernate bind 로그는 INFO로 낮춰두었습니다.

```yaml
logging:
  level:
    org.hibernate.SQL: INFO
    org.hibernate.orm.jdbc.bind: INFO
```

이유:

```text
TRACE로 두면 이력서 원문, 이메일, 전화번호 같은 민감한 정보가 로그에 그대로 찍힐 수 있습니다.
```

## 10. 다음 개선 후보

```text
1. Gemini API 에러 응답을 로그에 더 구체적으로 남기기
2. 503, 429 발생 시 다른 모델로 fallback하는 구조 추가
3. URL 모드에서 잡코리아처럼 동적 페이지인 경우 크롤링 실패 대비
4. 프론트에서 requirements 상세 결과를 카드 형태로 표시
```
