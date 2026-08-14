package com.project.wmsback.strategy.inspection.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 검수 제약 위반 전용 핸들러. 메시지에 더해 라인·규칙 단위 위반 목록을 실어 화면이 인라인 표시한다.
 * <p>
 * {@code common.exception.GlobalExceptionHandler}에 두지 않은 이유는 의존 방향이다 —
 * `common`은 어느 앱도 import하지 않는다.
 * <p>
 * {@code @Order} 필수 — advice가 여럿이면 예외 매칭은 advice 사이에서는 타입 구체성이 아니라
 * <b>advice 순서</b>가 먼저다. 순서가 없으면 스캔 순서상 앞선 common의 {@code Exception} 핸들러가
 * 위반 예외를 삼켜 500 「서버 오류」가 된다(2026-08-14 검수 화면 실제 결함). 이 핸들러가 잡지
 * 못한 예외만 공통 핸들러로 간다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class InspectionExceptionHandler {

    @ExceptionHandler(InspectionViolationException.class)
    public ResponseEntity<Map<String, Object>> handleInspectionViolationException(InspectionViolationException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage(), "violations", e.getViolations()));
    }
}
