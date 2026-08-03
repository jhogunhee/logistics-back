package com.project.wmsback.strategy.wave.dto;

import java.util.List;

/**
 * 웨이브 전략 실행 결과. 전략별로 "웨이브를 만들었는지 / 왜 안 만들었는지"를 돌려준다 —
 * 편입 0건이면 웨이브를 만들지 않으므로(빈 웨이브 없음) 재실행이 무해하다.
 */
public record WaveStgyExecResponse(
        int tgtCount,
        int assignedCount,
        List<StgyResult> results
) {

    /** 전략 1건의 실행 결과. wavId가 null이면 편입 0건이라 웨이브를 만들지 않은 것 */
    public record StgyResult(
            Long wavStgyId,
            String stgyNm,
            Long rvsnNo,
            Long wavId,
            String wavNo,
            int assignedCount,
            String skipRsn
    ) {
    }
}
