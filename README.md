# BackEnd

Spring Boot 기반 백엔드 프로젝트입니다.

## 기술 스택

- Java 17
- Spring Boot 4.1.0
- Spring Web MVC
- Spring Data JPA
- Spring Validation
- MySQL 8.0
- Gradle 9.5.1
- Docker Compose
- Lombok

## 실행 환경

프로젝트를 실행하려면 다음 도구가 필요합니다.

- JDK 17
- Docker 및 Docker Compose

Gradle은 프로젝트에 포함된 Gradle Wrapper를 사용하므로 별도로 설치하지 않아도 됩니다.

## 로컬 실행 방법

### 1. 프로젝트 경로로 이동

```bash
cd BackEnd
```

### 2. MySQL 실행

```bash
docker compose up -d
```

로컬 MySQL은 다음 설정으로 실행됩니다.

| 항목 | 값 |
|---|---|
| Host | `localhost` |
| Port | `3307` |
| Database | `resume` |
| Username | `root` |

컨테이너 상태는 다음 명령어로 확인할 수 있습니다.

```bash
docker compose ps
```

### 3. Spring Boot 실행

macOS 또는 Linux:

```bash
./gradlew bootRun
```

Windows:

```bash
gradlew.bat bootRun
```

실행이 완료되면 다음 주소에서 서버에 접근할 수 있습니다.

```text
http://localhost:8080
```

### 4. MySQL 종료

```bash
docker compose down
```

데이터까지 모두 삭제하려면 다음 명령어를 사용합니다.

```bash
docker compose down -v
```

`-v` 옵션은 MySQL 볼륨과 저장된 데이터를 삭제하므로 주의해야 합니다.

## 테스트

로컬 MySQL을 실행한 상태에서 전체 테스트를 실행합니다.

```bash
./gradlew test
```

테스트 결과는 다음 경로에서 확인할 수 있습니다.

```text
build/reports/tests/test/index.html
```

## Spring 프로필

### `local`

기본 활성 프로필입니다.

- Docker Compose로 실행한 로컬 MySQL을 사용합니다.
- 서버 포트는 `8080`입니다.
- SQL과 바인딩 값을 로그로 출력합니다.
- `ddl-auto: create`가 적용되어 애플리케이션 실행 시 기존 테이블과 데이터가 초기화될 수 있습니다.

### `prod`

운영 환경에서는 다음 환경변수가 필요합니다.

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

운영 프로필로 실행하는 예시는 다음과 같습니다.

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL='데이터베이스 주소' \
DB_USERNAME='데이터베이스 사용자' \
DB_PASSWORD='데이터베이스 비밀번호' \
./gradlew bootRun
```

실제 비밀번호나 API 키 같은 민감정보는 코드나 Git에 커밋하지 않습니다.

## 프로젝트 구조

```text
src
├── main
│   ├── java/com/backend
│   │   ├── BackEndApplication.java
│   │   └── global
│   │       ├── config       # 웹 및 공통 설정
│   │       ├── exception    # 공통 오류 코드와 전역 예외 처리
│   │       └── response     # 공통 API 응답 형식
│   └── resources
│       └── application.yml  # 환경별 애플리케이션 설정
└── test
    └── java/com/backend      # 테스트 코드
```

## 공통 API 응답

성공 응답은 다음 형식을 사용합니다.

```json
{
  "status": 200,
  "message": "OK",
  "data": {}
}
```

오류 응답은 다음 형식을 사용합니다.

```json
{
  "status": 404,
  "message": "리소스를 찾을 수 없습니다."
}
```

## 협업 규칙

- 브랜치에서 작업한 뒤 Pull Request를 생성합니다.
- 하나의 커밋과 Pull Request는 가능한 한 하나의 목적에 집중합니다.
- 커밋 메시지는 `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore` 등의 타입을 사용합니다.
- Pull Request에는 실제 변경 사항과 검증 결과를 작성합니다.
- 비밀번호, 토큰, API 키 등의 민감정보를 커밋하지 않습니다.
