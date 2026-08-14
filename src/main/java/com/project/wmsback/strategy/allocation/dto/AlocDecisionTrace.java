package com.project.wmsback.strategy.allocation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 할당 산정의 판정 근거 — 미리보기 응답과 실행 로그 dcsn_trc가 같은 모양을 쓴다.
 * 모양의 주인은 이 레코드다 (프론트 AllocPlanTrace가 이 키를 읽는다).
 * 값이 없는 항목은 직렬화에서 빠진다 — Map으로 만들던 시절의 「해당할 때만 키를 넣는다」와 같은 형태.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlocDecisionTrace(
        String prodCd,
        long reqQty,
        long asgnQty,
        /** 주문 정렬 기준 한 줄 (미설정이면 기본값 문구) */
        String odrSrt,
        /** 재고 정렬 기준 한 줄 */
        String invnSrt,
        /** 출고제약 구성 한 줄 */
        String rstrct,
        List<TierTrace> tiers,
        /** 제외 사유 (상한 MAX_SKIP_TRACE까지만) */
        List<SkipTrace> skips,
        /** 상한을 넘어 생략된 제외 건수. 없으면 생략 */
        Long skipsOmitted
) {

    /** 계층 1회의 판정. 후보·요청이 없어 건너뛰면 result만, 배정을 돌았으면 shortage 이하가 채워진다 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TierTrace(
            int seq,
            String cond,
            int cndtCnt,
            long avalQty,
            long reqQty,
            String result,
            Boolean shortage,
            List<DstrbTrace> dstrb,
            String sweep
    ) {
    }

    /** 분배 슬롯 1개의 배분 근거 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DstrbTrace(
            int seq,
            String cmpntCd,
            /** 슬롯 미등록 시 순차 소진 기본값으로 돌았음을 표시 */
            Boolean dflt,
            String cond,
            String result,
            Integer tgtLineCnt,
            Long asgnQty
    ) {
    }

    /** 제외된 (재고, 라인) 조합 1건 */
    public record SkipTrace(
            String outbNo,
            String storeCd,
            String locCd,
            String lotNo,
            String reason
    ) {
    }
}
