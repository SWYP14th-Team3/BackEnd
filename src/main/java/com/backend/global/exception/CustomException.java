package com.backend.global.exception;

import lombok.Getter;

/**
 * 서비스에서 예상 가능한 오류가 발생했을 때 사용하는 프로젝트 전용 예외입니다.
 *
 * <p>사용 예시 - ID에 해당하는 회원이 없을 때:</p>
 * <pre>{@code
 * public Member findMember(Long memberId) {
 *     return memberRepository.findById(memberId)
 *             .orElseThrow(() ->
 *                     new CustomException(ErrorCode.NOT_FOUND)
 *             );
 * }
 * }</pre>
 *
 * <p>위 코드에서 회원을 찾지 못하면 {@code CustomException}이 발생합니다.
 * 발생한 예외는 {@link GlobalExceptionHandler}가 처리하고,
 * {@link ErrorCode}에 저장된 HTTP 상태 코드와 메시지를 응답으로 전달합니다.</p>
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
