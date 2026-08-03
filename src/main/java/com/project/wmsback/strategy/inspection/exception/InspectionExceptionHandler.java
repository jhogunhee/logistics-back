package com.project.wmsback.strategy.inspection.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 검수 제약 위반 전용 핸들러. 메시지에 더해 라인·규칙 단위 위반 목록을 실어 화면이 인라인 표시한다.
 * <p>
 * {@code common.exception.GlobalExceptionHandler}에 두지 않은 이유는 의존 방향이다 —
 * `common`은 어느 앱도 import하지 않는다. {@code @RestControllerAdvice}는 여러 개 둘 수 있고
 * 더 구체적인 예외 타입이 먼저 매칭되므로, 이 핸들러가 잡지 못한 예외는 그대로 공통 핸들러로 간다.
 */
@RestControllerAdvice
public class InspectionExceptionHandler {

    @ExceptionHandler(InspectionViolationException.class)
    public ResponseEntity<Map<String, Object>> handleInspectionViolationException(InspectionViolationException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage(), "violations", e.getViolations()));
    }
}
