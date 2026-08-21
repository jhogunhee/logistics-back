package com.project.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException e) {
        return ResponseEntity
                .status(e.getStatusCode())
                .body(Map.of("message", e.getReason() != null ? e.getReason() : e.getMessage()));
    }

    // 검수 제약 위반(InspectionViolationException)은 여기서 다루지 않는다 — common이 wmsback을
    // import하지 않도록 wmsback.strategy.inspection.exception.InspectionExceptionHandler로 분리했다.
    // 주의: advice가 여럿이면 타입 구체성이 아니라 advice 순서가 먼저 매칭된다. 이 클래스는
    // Exception 최후 보루를 들고 있으므로, 전용 advice는 반드시 @Order로 이 클래스보다 앞서야 한다
    // (무순서 advice는 LOWEST 취급이라 스캔 순서상 common이 앞 — InspectionExceptionHandler 참고).

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", e.getMessage()));
    }

    /**
     * DB 제약 위반. SQLState로 원인을 구분한다 — 전부 "참조 중"으로 말하면 동시 중복 저장이
     * 실패했을 때 사용자에게 오답이 나간다 (이 DB는 FK가 0건이라 실제로 참조 위반은 없다).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", integrityMessage(e)));
    }

    private String integrityMessage(DataIntegrityViolationException e) {
        String state = null;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                state = sql.getSQLState();
                break;
            }
        }
        if (state == null) {
            return "데이터 제약 조건을 위반해 처리할 수 없습니다.";
        }
        return switch (state) {
            case "23505" -> "이미 존재하는 값입니다. 중복 여부를 확인하세요.";
            case "23502" -> "필수 값이 비어 있습니다.";
            case "23514" -> "허용 범위를 벗어난 값입니다.";
            default -> "데이터 제약 조건을 위반해 처리할 수 없습니다.";
        };
    }

    /**
     * 락 대기 상한(lock_timeout, 역할 단위 10s — docs/migration-lock-timeout.sql) 초과.
     * CannotAcquireLockException 이 이 타입의 하위라 함께 잡힌다. 데이터는 멀쩡하고 다시 시도하면 된다.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handlePessimisticLockingFailureException(PessimisticLockingFailureException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", "다른 작업이 같은 대상을 처리 중입니다 — 잠시 후 다시 시도하세요."));
    }

    /** 문장 상한(statement_timeout, Supabase 기본 2min) 초과. 락 대기가 아니라 처리 자체가 길었던 경우다 */
    @ExceptionHandler(QueryTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleQueryTimeoutException(QueryTimeoutException e) {
        log.error("문장 시간 상한 초과", e);
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "처리가 너무 오래 걸려 중단했습니다 — 대상을 줄여 다시 시도하세요."));
    }

    /** 없는 경로 호출. Exception 핸들러가 삼켜 500으로 응답하지 않도록 분리 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFoundException(NoResourceFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "존재하지 않는 경로입니다: /" + e.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "서버 오류가 발생했습니다."));
    }
}