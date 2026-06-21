# CustomException 이해하기

## 1. CustomException은 왜 만들었는가?

`CustomException`은 프로젝트에서 발생하는 **예상 가능한 오류를 일관된 HTTP 응답으로 전달하기 위해 만든 사용자 정의 예외 클래스**입니다.

예를 들어 회원을 조회했는데 존재하지 않는 경우를 생각해 봅시다.

Java의 기본 예외만 사용하면 다음과 같이 작성할 수 있습니다.

```java
throw new RuntimeException("회원을 찾을 수 없습니다.");
```

하지만 이렇게 작성하면 다음과 같은 문제가 생길 수 있습니다.

- 어떤 HTTP 상태 코드를 보낼지 명확하지 않습니다.
- 서비스마다 오류 메시지와 응답 형식이 달라질 수 있습니다.
- 개발자가 상태 코드와 메시지를 매번 직접 작성해야 합니다.
- 예상한 오류와 실제 서버 오류를 구분하기 어렵습니다.

이 프로젝트에서는 사용할 오류를 `ErrorCode`로 정리하고, 선택한 오류 정보를 전달하기 위해 `CustomException`을 사용합니다.

```java
throw new CustomException(ErrorCode.NOT_FOUND);
```

이렇게 예외를 발생시키면 `ErrorCode.NOT_FOUND`에 들어 있는 `404 Not Found` 상태 코드와 오류 메시지를 사용하여 응답할 수 있습니다.

## 2. CustomException 파일 위치

```text
src/main/java/com/backend/global/exception/CustomException.java
```

현재 코드는 다음과 같습니다.

```java
package com.backend.global.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

## 3. 코드를 한 줄씩 이해하기

### `@Getter`

```java
@Getter
```

Lombok이 `errorCode` 값을 꺼내기 위한 `getErrorCode()` 메서드를 자동으로 만들어 줍니다.

직접 작성한다면 다음과 같은 코드입니다.

```java
public ErrorCode getErrorCode() {
    return errorCode;
}
```

따라서 다른 클래스에서 다음처럼 오류 정보를 꺼낼 수 있습니다.

```java
ErrorCode errorCode = e.getErrorCode();
```

### `extends RuntimeException`

```java
public class CustomException extends RuntimeException
```

`CustomException`을 Java의 예외로 사용하기 위해 `RuntimeException`을 상속합니다.

따라서 다음과 같이 `throw`로 예외를 발생시킬 수 있습니다.

```java
throw new CustomException(ErrorCode.NOT_FOUND);
```

`RuntimeException`을 상속했기 때문에 메서드 선언에 다음과 같은 `throws`를 반드시 작성하지 않아도 됩니다.

```java
public Member findMember() throws CustomException
```

또한 Spring에서 처리되지 않은 `RuntimeException`이 발생하면 일반적으로 진행 중인 데이터베이스 트랜잭션도 롤백됩니다.

### `errorCode`

```java
private final ErrorCode errorCode;
```

어떤 종류의 오류가 발생했는지를 보관하는 필드입니다.

`ErrorCode`에는 다음 정보가 들어 있습니다.

```text
HTTP 상태 코드
사용자에게 전달할 오류 메시지
```

`final`이 붙어 있으므로 생성자에서 한 번 저장한 후 다른 값으로 바꿀 수 없습니다.

### 생성자

```java
public CustomException(ErrorCode errorCode) {
```

예외를 만들 때 어떤 오류가 발생했는지를 전달받습니다.

```java
new CustomException(ErrorCode.NOT_FOUND)
```

### 부모 예외에 메시지 전달하기

```java
super(errorCode.getMessage());
```

부모 클래스인 `RuntimeException`의 생성자에 오류 메시지를 전달합니다.

덕분에 다음과 같이 기본 예외 메서드로도 메시지를 가져올 수 있습니다.

```java
e.getMessage();
```

### ErrorCode 저장하기

```java
this.errorCode = errorCode;
```

생성자로 전달받은 `ErrorCode`를 현재 `CustomException` 객체에 저장합니다.

나중에 `GlobalExceptionHandler`가 이 값을 꺼내서 HTTP 상태 코드와 응답 메시지를 결정합니다.

## 4. ErrorCode는 무엇인가?

파일 위치는 다음과 같습니다.

```text
src/main/java/com/backend/global/exception/ErrorCode.java
```

현재 프로젝트에는 다음과 같은 오류들이 정의되어 있습니다.

```java
INVALID_INPUT_VALUE(
    HttpStatus.BAD_REQUEST,
    "유효하지 않은 입력값입니다."
),

INTERNAL_SERVER_ERROR(
    HttpStatus.INTERNAL_SERVER_ERROR,
    "서버 오류가 발생했습니다."
),

NOT_FOUND(
    HttpStatus.NOT_FOUND,
    "리소스를 찾을 수 없습니다."
),

UNAUTHORIZED(
    HttpStatus.UNAUTHORIZED,
    "인증이 필요합니다."
),

FORBIDDEN(
    HttpStatus.FORBIDDEN,
    "접근 권한이 없습니다."
);
```

각 오류는 두 가지 정보를 가집니다.

```java
private final HttpStatus httpStatus;
private final String message;
```

예를 들어 `NOT_FOUND`에는 다음 정보가 들어 있습니다.

```text
HTTP 상태: 404 Not Found
메시지: 리소스를 찾을 수 없습니다.
```

## 5. CustomException 사용 방법

예를 들어 ID로 회원을 조회하는 서비스를 만든다고 생각해 봅시다.

```java
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    public Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.NOT_FOUND)
                );
    }
}
```

회원을 찾았다면 `Member` 객체를 반환합니다.

```text
회원 존재 → Member 반환
```

회원을 찾지 못했다면 `CustomException`을 발생시킵니다.

```text
회원 없음
→ CustomException 발생
→ ErrorCode.NOT_FOUND 전달
```

위 코드를 `if` 문 형태로 풀어서 표현하면 다음과 같은 의미입니다.

```java
Optional<Member> member = memberRepository.findById(memberId);

if (member.isEmpty()) {
    throw new CustomException(ErrorCode.NOT_FOUND);
}

return member.get();
```

## 6. 발생한 예외는 누가 처리하는가?

`CustomException`은 다음 파일의 `GlobalExceptionHandler`가 처리합니다.

```text
src/main/java/com/backend/global/exception/GlobalExceptionHandler.java
```

관련 코드는 다음과 같습니다.

```java
@ExceptionHandler(CustomException.class)
public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
    ErrorCode errorCode = e.getErrorCode();
    log.warn("CustomException: {}", errorCode.getMessage());

    return ResponseEntity
            .status(errorCode.getHttpStatus())
            .body(
                ApiResponse.error(
                    errorCode.getHttpStatus().value(),
                    errorCode.getMessage()
                )
            );
}
```

`CustomException`이 발생하면 `GlobalExceptionHandler`가 자동으로 감지합니다.

따라서 컨트롤러에서 매번 `try-catch`를 작성할 필요가 없습니다.

```text
서비스에서 CustomException 발생
        ↓
GlobalExceptionHandler가 예외 감지
        ↓
예외에 저장된 ErrorCode 꺼내기
        ↓
HTTP 상태 코드와 메시지 결정
        ↓
ApiResponse 형식으로 프론트엔드에 응답
```

## 7. 실제 오류 응답

다음과 같은 예외가 발생했다고 가정해 봅시다.

```java
throw new CustomException(ErrorCode.NOT_FOUND);
```

`GlobalExceptionHandler`는 `NOT_FOUND`에 들어 있는 다음 정보를 사용합니다.

```text
상태 코드: 404
메시지: 리소스를 찾을 수 없습니다.
```

프론트엔드에는 다음과 같은 JSON 응답이 전달됩니다.

```json
{
  "status": 404,
  "message": "리소스를 찾을 수 없습니다."
}
```

`ApiResponse`의 `data`는 `null`이지만 다음 설정 때문에 JSON 결과에서 제외됩니다.

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
```

## 8. 구체적인 오류 코드 추가하기

현재 `NOT_FOUND` 메시지는 여러 종류의 리소스에 공통으로 사용할 수 있지만 조금 포괄적입니다.

프로젝트가 커지면 `ErrorCode`에 다음과 같이 구체적인 오류를 추가할 수 있습니다.

```java
MEMBER_NOT_FOUND(
    HttpStatus.NOT_FOUND,
    "회원을 찾을 수 없습니다."
),

POST_NOT_FOUND(
    HttpStatus.NOT_FOUND,
    "게시글을 찾을 수 없습니다."
),

DUPLICATE_LOGIN_ID(
    HttpStatus.CONFLICT,
    "이미 사용 중인 아이디입니다."
);
```

서비스에서는 발생한 문제에 알맞은 오류 코드를 선택합니다.

```java
throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
```

```java
throw new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
```

## 9. 전체 동작 흐름

```text
1. 서비스에서 예상 가능한 문제가 발생한다.
2. 문제에 알맞은 ErrorCode를 선택한다.
3. 선택한 ErrorCode를 담아 CustomException을 던진다.
4. GlobalExceptionHandler가 CustomException을 잡는다.
5. ErrorCode에서 HTTP 상태 코드와 메시지를 꺼낸다.
6. ApiResponse 형식으로 프론트엔드에 오류를 전달한다.
```

## 10. 한 문장으로 정리

`CustomException`은 **서비스에서 발생한 예상 가능한 문제를 `ErrorCode`와 함께 전달하고, 그 문제를 일관된 HTTP 오류 응답으로 바꾸기 위해 만든 프로젝트 전용 예외 클래스**입니다.

> 현재 프로젝트에는 아직 `CustomException`을 실제로 던지는 서비스 코드가 없습니다. 회원, 게시글 등의 기능을 구현하면서 필요한 위치에서 사용하면 됩니다.
