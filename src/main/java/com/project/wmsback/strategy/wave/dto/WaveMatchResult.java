package com.project.wmsback.strategy.wave.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 주문 1건의 판정 결과 — 미리보기 응답이자 실행 로그 dcsn_trc의 원소.
 * "왜 이 주문이 편성 안 됐죠?"에 조건 단위로 답하는 데이터다 (P5).
 */
public record WaveMatchResult(
        Long outbOrderId,
        String outbNo,
        String outbTyp,
        String vhclFltno,
        String storeCd,
        String storeNm,
        LocalDate expctDe,
        boolean matched,
        List<GroupTrace> grps
) {

    /** 조건그룹 1개의 판정 (그룹끼리 OR — 하나라도 pass면 편입) */
    public record GroupTrace(int idx, boolean pass, List<CondTrace> conds) {
    }

    /** 조건 1건의 판정 (그룹 안은 AND). actual = 주문의 실제값, expected = 전략에 저장된 값 */
    public record CondTrace(String fld, String op, List<String> expected, String actual, boolean pass) {
    }
}
