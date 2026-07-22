# Gemini 이력서-채용공고 분석 API 작업 정리

이 문서는 PDF 이력서와 채용공고를 Gemini API로 비교 분석하는 기능의 현재 작업 내용을 정리한 문서입니다.

## 1. 기능 목표

사용자가 프론트엔드에서 아래 값을 업로드하면 백엔드가 Gemini API를 호출해서 이력서와 채용공고의 적합도를 분석합니다.

```text
PDF 이력서
채용공고 URL 또는 채용공고 텍스트
사용자 ID
```

분석 결과는 아래 테이블에 저장됩니다.

```text
analysis_result
job_requirement
requirement_evaluation
```

## 2. 현재 API

현재 컨트롤러 위치:

```text
src/main/java/com/backend/analysis_20260710/controller/AnalysisController.java
```

엔드포인트:

```http
POST /api/analyses
Content-Type: multipart/form-data
```

요청 필드:

| 필드 | 필수 여부 | 설명 |
| --- | --- | --- |
| `userId` | 필수 | 임시 사용자 ID입니다. 현재는 `test-user-1` 같은 문자열을 받을 수 있습니다. |
| `jobPostingMode` | 선택 | `url` 또는 `text`입니다. 기본값은 `url`입니다. |
| `jobPostingUrl` | URL 모드일 때 필요 | 채용공고 URL입니다. |
| `jobUrl` | URL 모드일 때 선택 | `jobPostingUrl` 대신 받을 수 있는 호환용 필드입니다. |
| `jobPostingText` | text 모드일 때 필요 | 채용공고 원문 텍스트입니다. |
| `file` | 선택 | 프론트에서 보내는 PDF 파일 필드입니다. |
| `resumePdf` | 선택 | 백엔드 테스트용 또는 호환용 PDF 파일 필드입니다. |

`file`과 `resumePdf` 중 하나는 반드시 있어야 합니다.

## 3. 전체 처리 흐름

```text
Frontend
  -> POST /api/analyses
  -> multipart/form-data 전송

AnalysisController
  -> file 또는 resumePdf 중 실제 PDF 선택
  -> jobPostingUrl 또는 jobUrl 중 실제 URL 선택
  -> AnalysisService 호출

AnalysisService
  -> PDF 파일 검증
  -> jobPostingMode를 TEXT 또는 URL로 정규화
  -> TEXT 모드면 jobPostingText 사용
  -> URL 모드면 JobPostingCrawler로 채용공고 페이지 텍스트 추출
  -> Gemini 프롬프트 생성
  -> GeminiAnalysisClient 호출
  -> Gemini 응답을 DTO로 파싱
  -> 분석 결과 DB 저장
  -> 응답 DTO 반환

GeminiAnalysisClient
  -> PDF를 Base64로 인코딩
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
src/main/java/com/backend/analysis_20260710/service/AnalysisService.java
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

그래서 프롬프트의 반환 JSON 형식을 현재 DTO와 맞게 아래 구조로 변경했습니다.

```json
{
  "companyName": "회사명",
  "positionTitle": "포지션명",
  "overallLevel": "HIGH 또는 MEDIUM 또는 LOW",
  "resumeOriginalText": "PDF 이력서에서 추출한 핵심 텍스트",
  "requirements": [
    {
      "category": "REQUIRED 또는 WORK_SKILL 또는 DOMAIN 또는 PREFERRED",
      "title": "요건명",
      "description": "요건 설명",
      "sourceText": "채용공고에서 해당 요건을 판단한 원문 근거",
      "matchStatus": "CONFIRMED 또는 NEEDS_IMPROVEMENT 또는 MISSING",
      "resumeEvidence": "이력서 근거",
      "feedback": "진단 이유",
      "revisionSuggestion": "수정 제안"
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
MISSING -> redCount
NEEDS_IMPROVEMENT -> yellowCount
CONFIRMED -> greenCount
```

Gemini가 이상한 값을 내려줘도 아래 메서드에서 기본값으로 보정합니다.

```text
normalizeCategory()
normalizeMatchStatus()
normalizeOverallLevel()
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
  -F "file=@/Users/banjaehyeon/Desktop/이력서/반재현_이력서_260628.pdf;type=application/pdf" \
  -F "userId=test-user-1" \
  -F "jobPostingMode=text" \
  -F "jobPostingText=Java Spring Boot 백엔드 개발자 채용. 필수요건은 Java, Spring Boot, MySQL 경험입니다. 우대사항은 Redis 경험입니다."
```

성공 응답 예시:

```json
{
  "data": {
    "analysisResultId": 2,
    "userId": "test-user-1",
    "jobUrl": null,
    "jobPlatform": "TEXT",
    "companyName": "해당없음",
    "positionTitle": "Java Spring Boot 백엔드 개발자",
    "overallLevel": "HIGH",
    "redCount": 0,
    "yellowCount": 1,
    "greenCount": 2,
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
id: 2
user_id: test-user-1
job_input_type: TEXT
job_platform: TEXT
company_name: 해당없음
position_title: Java Spring Boot 백엔드 개발자
overall_level: HIGH
red_count: 0
yellow_count: 1
green_count: 2
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
1. 패키지명 analysis_20260710을 analysis로 정리
2. 사용하지 않는 buildPrompt01 제거
3. Gemini API 에러 응답을 로그에 더 구체적으로 남기기
4. 503, 429 발생 시 다른 모델로 fallback하는 구조 추가
5. URL 모드에서 잡코리아처럼 동적 페이지인 경우 크롤링 실패 대비
6. 프론트에서 requirements 상세 결과를 카드 형태로 표시
```
