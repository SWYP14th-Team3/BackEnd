# CORS와 WebConfig 이해하기

## 1. CORS란 무엇인가?

CORS는 **Cross-Origin Resource Sharing**의 약자입니다.

쉽게 말하면 다음을 정하는 브라우저의 보안 규칙입니다.

> 서로 다른 출처의 프로그램끼리 데이터를 주고받아도 되는가?

여기서 출처(Origin)는 다음 세 가지의 조합입니다.

```text
프로토콜 + 도메인 + 포트번호
```

예를 들어 프론트엔드와 백엔드가 다음 주소에서 실행된다고 생각해 봅시다.

```text
프론트엔드: http://localhost:5173
백엔드:     http://localhost:8080
```

도메인은 모두 `localhost`이지만 포트번호가 다릅니다.

```text
5173 != 8080
```

따라서 브라우저는 두 주소를 서로 다른 출처로 판단합니다.

## 2. CORS 문제란 무엇인가?

프론트엔드에서 다음과 같이 백엔드 API를 호출할 수 있습니다.

```javascript
fetch("http://localhost:8080/api/members");
```

브라우저는 이 요청을 확인하고 다음과 같이 판단합니다.

```text
localhost:5173에서 실행 중인 프로그램이
localhost:8080의 데이터를 요청하고 있다.

백엔드가 허용했는지 확인할 수 없으므로 요청을 차단해야겠다.
```

백엔드가 다른 출처의 요청을 허용하지 않았다면 브라우저 콘솔에 다음과 비슷한 오류가 발생합니다.

```text
Access to fetch has been blocked by CORS policy
```

이것이 일반적으로 말하는 **CORS 문제**입니다.

서버 자체가 고장 난 것이 아니라, 브라우저가 보안을 위해 응답의 사용을 차단한 것입니다.

## 3. CORS 보안 규칙은 왜 필요한가?

사용자가 은행 사이트에 로그인한 상태라고 생각해 봅시다.

브라우저에 CORS 같은 보안 규칙이 없다면 악성 사이트가 사용자 몰래 은행 서버에 요청을 보내고 개인정보를 읽으려고 시도할 수 있습니다.

그래서 브라우저는 기본적으로 다음과 같이 행동합니다.

> 다른 출처에서 보낸 요청은 서버가 명확하게 허용한 경우에만 사용할 수 있게 하자.

백엔드는 CORS 설정을 통해 허용할 출처와 요청 방법을 브라우저에 알려줍니다.

## 4. 이 프로젝트의 WebConfig 역할

이 프로젝트의 `WebConfig`는 백엔드 전체에 CORS 허용 규칙을 등록합니다.

파일 위치는 다음과 같습니다.

```text
src/main/java/com/backend/global/config/WebConfig.java
```

현재 코드는 다음과 같습니다.

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

## 5. 코드를 한 줄씩 이해하기

### `@Configuration`

```java
@Configuration
```

Spring에게 이 클래스가 설정 파일이라는 것을 알려줍니다.

Spring Boot가 실행될 때 이 클래스를 자동으로 발견하고 설정을 적용하므로 개발자가 `new WebConfig()`처럼 직접 객체를 만들 필요가 없습니다.

### `implements WebMvcConfigurer`

```java
public class WebConfig implements WebMvcConfigurer
```

Spring MVC의 웹 설정을 추가하거나 변경할 수 있게 해주는 인터페이스입니다.

Spring의 기본 웹 기능을 그대로 사용하면서 필요한 설정을 추가한다는 뜻입니다.

### `addCorsMappings()`

```java
@Override
public void addCorsMappings(CorsRegistry registry)
```

CORS 허용 규칙을 등록하는 메서드입니다. Spring이 서버를 시작하면서 자동으로 실행합니다.

### 모든 API에 적용하기

```java
registry.addMapping("/**")
```

`/**`는 모든 주소를 뜻합니다. 따라서 다음과 같은 모든 API에 CORS 설정이 적용됩니다.

```text
/api/members
/api/posts
/api/login
```

특정 API에만 적용하려면 다음처럼 작성할 수도 있습니다.

```java
registry.addMapping("/api/**")
```

### 모든 출처 허용하기

```java
.allowedOriginPatterns("*")
```

`*`는 모든 출처를 뜻합니다. 현재 설정에서는 다음과 같은 여러 출처의 요청을 허용합니다.

```text
http://localhost:5173
http://localhost:3000
https://다른사이트.com
```

개발 중에는 편리하지만 운영 환경에서는 보안상 실제 프론트엔드 주소만 허용하는 것이 좋습니다.

```java
.allowedOriginPatterns(
    "http://localhost:5173",
    "https://my-frontend.com"
)
```

### HTTP 요청 종류 허용하기

```java
.allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
```

각 요청 방법의 의미는 다음과 같습니다.

- `GET`: 데이터 조회
- `POST`: 데이터 생성
- `PUT`: 데이터 전체 수정
- `PATCH`: 데이터 일부 수정
- `DELETE`: 데이터 삭제
- `OPTIONS`: 실제 요청 전에 허용 여부 확인

### OPTIONS 사전 요청

브라우저는 조건에 따라 실제 요청을 보내기 전에 백엔드에 허용 여부를 먼저 확인합니다.

```text
1. 브라우저가 OPTIONS 요청을 보낸다.
2. 백엔드가 요청 허용 여부를 응답한다.
3. 허용되면 브라우저가 실제 POST, PUT 등의 요청을 보낸다.
4. 백엔드가 실제 요청의 결과를 응답한다.
```

이러한 `OPTIONS` 요청을 **Preflight 요청**, 즉 사전 확인 요청이라고 합니다.

### 모든 요청 헤더 허용하기

```java
.allowedHeaders("*")
```

프론트엔드가 보내는 모든 요청 헤더를 허용합니다.

대표적인 요청 헤더는 다음과 같습니다.

```text
Content-Type: application/json
Authorization: Bearer 토큰값
```

### 쿠키와 인증정보 허용하기

```java
.allowCredentials(true)
```

쿠키와 같은 인증정보를 포함한 요청을 허용합니다.

프론트엔드에서 `fetch`를 사용한다면 쿠키를 보내도록 별도로 설정해야 합니다.

```javascript
fetch("http://localhost:8080/api/members", {
  credentials: "include"
});
```

Axios를 사용한다면 다음과 같이 설정합니다.

```javascript
axios.get("http://localhost:8080/api/members", {
  withCredentials: true
});
```

백엔드의 `allowCredentials(true)`만으로 쿠키가 자동 전송되는 것은 아닙니다. 프론트엔드에서도 인증정보 포함 옵션을 설정해야 합니다.

### 사전 요청 결과 저장하기

```java
.maxAge(3600)
```

브라우저가 CORS 사전 확인 결과를 3600초, 즉 1시간 동안 기억하게 합니다.

동일한 요청을 보낼 때마다 `OPTIONS` 요청을 반복하지 않아도 되므로 불필요한 요청을 줄일 수 있습니다.

## 6. WebConfig는 어떻게 사용되는가?

개발자가 `WebConfig`를 직접 호출하지 않습니다.

```text
Spring Boot 실행
        ↓
@Configuration이 붙은 WebConfig 발견
        ↓
addCorsMappings() 자동 실행
        ↓
CORS 규칙 등록
        ↓
프론트엔드가 백엔드 API 요청
        ↓
등록된 CORS 규칙에 따라 요청 허용
```

## 7. 한 문장으로 정리

CORS는 **다른 출처에서 백엔드에 접근하는 것을 브라우저가 기본적으로 제한하는 보안 규칙**이고, `WebConfig`는 **어떤 출처와 요청을 허용할지 Spring 전체에 등록하는 설정 파일**입니다.
