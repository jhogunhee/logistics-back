package com.project.wmsback.outbound.dto;

import java.util.List;

/**
 * 자동할당 실행 결과. <b>동기 실행이므로 결과를 그대로 돌려준다</b> —
 * 성공·부족·판정 근거를 화면에서 바로 볼 수 있어야 한다.
 *
 * <p>{@code shortLines}(잔량이 남은 라인)가 이 프로젝트의 결품 보고다. 별도 테이블을 두지 않고
 * 실행 응답과 웨이브 상세 조회 양쪽에서 파생값으로 보여준다.
 */
public record AllocExecuteResponse(
        int waveCount,
        int lineCount,
        long reqQty,
        long alocQty,
        long shortQty,
        /** 이번 실행에 적용된 할당 전략. 전부 null = 전략 미설정, 기본 동작(FEFO·순차 소진)으로 실행됨 */
        Long alocStgyId,
        String stgyNm,
        Long rvsnNo,
        List<LineResult> lines
) {

    /** 전략 없이 실행된 경우 (수동할당 · 매칭 전략 없음) */
    public static AllocExecuteResponse of(int waveCount, int lineCount, long reqQty, long alocQty,
                                          List<LineResult> lines) {
        return new AllocExecuteResponse(waveCount, lineCount, reqQty, alocQty, reqQty - alocQty,
                null, null, null, lines);
    }
    /**
     * 라인 1건의 처리 결과.
     *
     * @param skips 후보에서 빠진 재고와 그 사유 — 조용히 빠지는 재고를 만들지 않기 위한 판정 근거
     */
    public record LineResult(
            Long outbLineId,
            String outbNo,
            String prodCd,
            long reqQty,
            long alocQty,
            long shortQty,
            List<Assignment> assignments,
            List<Skip> skips
    ) {
    }

    /** 이번 실행에서 이 라인에 붙은 재고 한 건 */
    public record Assignment(Long invId, String locCd, String lotNo, long qty) {
    }

    /** 후보에서 제외된 재고 한 건과 사유 */
    public record Skip(Long invId, String locCd, String lotNo, String reason) {
    }
}
