## 작업 내용

<!-- 이 PR에서 실제로 변경한 내용을 작성해 주세요. -->

- Gemini 기반 이력서 PDF/채용공고 분석 API와 재분석 API를 추가했습니다.
- 분석 결과 저장을 위한 `analysis_result`, `job_requirement`, `requirement_evaluation`, `user_resume`, `job_description` 흐름을 구성했습니다.
- `References_01.json`의 판정 기준표와 피드백 예시를 프롬프트에 주입하도록 구현했습니다.
- 재분석 시 기존 `analysis_result_id` 기준으로 분석 결과와 요건/평가 데이터를 갱신하도록 구현했습니다.
- 재분석 서비스 단위 테스트와 작업 요약 문서를 추가했습니다.
- 전역 설정/예외/응답 코드와 분석 관련 코드에 주석 규격을 맞춰 보강했습니다.

## 관련 이슈

<!-- 예: Closes #123 / 관련 이슈가 없다면 `없음`으로 작성해 주세요. -->

- Closes #없음

## 테스트

<!-- 실행한 테스트 또는 직접 확인한 내용을 작성해 주세요. -->

- [x] 테스트 코드 실행
- [ ] 직접 동작 확인
- 확인 내용: `./gradlew test` 실행 결과 `BUILD SUCCESSFUL` 확인

## 참고 사항

<!-- 리뷰어가 알아야 할 API, DB, 설정 변경이나 집중해서 볼 부분이 있다면 작성해 주세요. 없다면 `없음`으로 작성해 주세요. -->

- 신규 분석 API: `POST /api/analyses`
- 신규 재분석 API: `POST /api/analysesRe`
- 재분석 요청값: `analysis_result_id`, `resume_content`, `jd_content`
- 재분석은 `retryCount` 기준 최대 5회까지 허용됩니다.
- 기본 Gemini 모델은 `gemini-3.1-flash-lite`로 설정되어 있습니다.
