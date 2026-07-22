package com.backend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int status;
    private String message;
    private T data;

    private ApiResponse(int status, String message, T data) {
        // 모든 API 응답의 공통 필드 설정
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        // 데이터가 있는 성공 응답 생성
        return new ApiResponse<>(200, "OK", data);
    }

    public static ApiResponse<Void> success() {
        // 데이터가 없는 성공 응답 생성
        return new ApiResponse<>(200, "OK", null);
    }

    public static ApiResponse<Void> error(int status, String message) {
        // 실패 응답 생성
        return new ApiResponse<>(status, message, null);
    }
}
