## 요약

- URL과 채용공고 text가 함께 입력되면 크롤링 본문 대신 사용자가 입력한 text를 LLM1 입력으로 사용합니다.
- `jd_original_text`는 LLM1의 `raw_text`가 아니라 사용자가 입력한 채용공고 text 그대로 저장되도록 변경했습니다.
- LLM1 응답은 회사명/포지션/요약 추출에 사용하고, 저장용 원문은 별도로 보존합니다.
- 실제 LLM 수동 테스트에서 DB 저장 결과를 확인하도록 보강했습니다.

## 테스트

- [x] `./gradlew test --tests com.backend.analysis.application.AnalysisServiceTest`
- [x] `./gradlew test --tests com.backend.analysis.application.AnalysisManualLlmTest`
- [ ] API 동작 확인

## 참고

- URL만 입력하면 기존처럼 URL 크롤링 결과를 사용합니다.
- URL과 text를 함께 보내면 `job_url`, `job_platform`은 유지하되 본문 원문 저장 기준은 text 입력값입니다.
- 분석용 요약도 사용자가 입력한 채용공고 text를 기반으로 생성됩니다.
- 수동 LLM 테스트는 `RUN_LLM_ANALYSIS_TEST=true`와 `gemini.api-key` 설정이 있을 때만 실제 분석/DB 저장을 수행합니다.
- `.DS_Store` 미추적 파일은 PR 변경 범위에 포함하지 않았습니다.
