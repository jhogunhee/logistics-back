package com.project.wmsback.strategy.putaway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 적치 추천의 판정 근거 — 미리보기 응답과 실행 로그 dcsn_trc가 같은 모양을 쓴다.
 * 모양의 주인은 이 레코드다 (프론트 PreviewPanel·ExecutionHistory가 이 키를 읽는다).
 * 값이 없는 항목은 직렬화에서 빠진다 — Map으로 만들던 시절의 「해당할 때만 키를 넣는다」와 같은 형태.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PutawayDecisionTrace(
        long reqQty,
        long asgnQty,
        List<StageTrace> stages
) {

    /** 단계 1개의 게이트 판정. locs는 게이트 통과(PASS) 시에만 채워진다 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StageTrace(
            Integer srtSeq,
            String mthdCd,
            String gate,
            List<LocTrace> locs
    ) {
    }

    /** 후보 로케이션 1개의 배정 근거 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LocTrace(
            String locCd,
            long avalQty,
            long asgnQty,
            /** 미완료 지시가 이미 잡아둔 자리. 0이면 생략 */
            Long inflowQty,
            /** 같은 일괄 추천의 앞선 배치가 잡아둔 자리. 0이면 생략 */
            Long crossQty,
            /** max_qty 미설정 경고. 해당 없으면 생략 */
            String warn,
            /** 배정 0일 때의 사유. 배정됐으면 생략 */
            String skip
    ) {
    }
}
