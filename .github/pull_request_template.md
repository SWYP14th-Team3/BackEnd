## Summary

- URL과 채용공고 text가 함께 입력되면 사용자가 입력한 text를 우선 분석하도록 변경했습니다.
- 잡코리아 등 크롤링 결과가 요약/모집요강 텍스트로 들어와도 직접 입력한 공고 원문이 `jd_original_text`에 저장되도록 했습니다.
- 실제 LLM 수동 테스트가 Spring context와 실제 DB repository를 사용해 저장 여부를 확인하도록 보강했습니다.

## Test

- [x] `./gradlew test --tests com.backend.analysis.application.AnalysisServiceTest`
- [x] `RUN_LLM_ANALYSIS_TEST=true ./gradlew --no-daemon test --tests com.backend.analysis.application.AnalysisManualLlmTest --rerun-tasks`
- [ ] API 동작 확인

## Note

- URL과 채용공고 text를 함께 보내면 크롤링 결과보다 text 입력값이 우선됩니다.
- 수동 LLM 테스트는 `RUN_LLM_ANALYSIS_TEST=true`와 `gemini.api-key` 설정이 있을 때만 실제 분석/DB 저장을 수행합니다.
- `.DS_Store` 미추적 파일은 PR 변경 범위에 포함하지 않았습니다.
