package com.project.wmsback.strategy.inspection.exception;

import lombok.Getter;

import java.util.List;

/**
 * 검수 제약 위반 — 검수 저장 전체를 거부한다 (요청 라인 중 한 건이라도 실패하면 전체 롤백이라는
 * 기존 검수 트랜잭션 단위의 계승). 위반은 첫 건에서 중단하지 않고 전부 수집해 한 번에 담는다 —
 * 관리자가 하나 고치면 다음 위반이 또 나오는 UX를 피한다.
 */
@Getter
public class InspectionViolationException extends RuntimeException {

    /** 라인 단위 위반. 화면이 라인 행에 인라인으로 표시한다 */
    public record LineViolation(Long ibLineId, String prodCd, String ruleCd, String ruleName,
                                String message, String actual, String expected) {
    }

    private final transient List<LineViolation> violations;

    public InspectionViolationException(List<LineViolation> violations) {
        super("검수 제약 위반 " + violations.size() + "건 — 검수 저장이 거부되었습니다.");
        this.violations = violations;
    }
}
