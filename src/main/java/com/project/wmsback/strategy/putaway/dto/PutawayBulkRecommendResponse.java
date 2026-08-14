package com.project.wmsback.strategy.putaway.dto;

import java.util.List;

/**
 * 적치지시 일괄 추천 결과 — 배치마다 (로케이션, 수량) N행.
 * <p>
 * 단건 추천(PutawayRecommendResponse)과 달리 단계별 근거(trace)는 담지 않는다. 배치가 여러 개면
 * 응답이 급격히 커지는데 화면이 쓰는 것은 배정 결과뿐이고, 근거는 전략 관리 화면의 미리보기가 맡는다
 * (실행 로그에는 배치별로 그대로 남는다).
 */
public record PutawayBulkRecommendResponse(List<Item> items) {

    public record Item(
            Long ibLineId,
            Long lotId,
            String prodCd,
            String prodNm,
            /** 전략 미설정이면 false — 화면은 그 배치를 수동 지시로 안내한다 */
            boolean strategySelected,
            String stgyNm,
            Long rvsnNo,
            long reqQty,
            long asgnQty,
            /** 배정하지 못한 잔량 (로케이션 용량 부족). 0보다 크면 화면이 경고한다 */
            long remainQty,
            List<Assignment> assignments
    ) {
    }

    /** 배정 1행 — 이 로케이션에 이만큼. 그대로 적치지시 한 건이 된다 */
    public record Assignment(Long locId, String locCd, long qty) {
    }
}
