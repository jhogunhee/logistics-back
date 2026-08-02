package com.project.wmsback.strategy.putaway.dto;

import java.util.List;

/**
 * 적치 추천/미리보기 결과. strategySelected=false면 화면은 기존 수동 후보 목록으로 폴백한다.
 * trace는 단계별 게이트·후보별 배정 근거 — PreviewPanel의 "게이트 표" 원본이다.
 * actualSelected*는 미리보기 전용: 이 대상에 실제로 선택될 전략이 편집 중인 전략과 다르면 화면이 경고한다.
 */
public record PutawayRecommendResponse(
        boolean strategySelected,
        Long ptawyStgyId,
        String stgyNm,
        Long rvsnNo,
        long reqQty,
        long asgnQty,
        long remainQty,
        List<Assignment> assignments,
        Object trace,
        Long actualSelectedStgyId,
        String actualSelectedStgyNm
) {

    public record Assignment(Long locId, String locCd, long qty) {
    }

    public static PutawayRecommendResponse noStrategy(long reqQty) {
        return new PutawayRecommendResponse(false, null, null, null, reqQty, 0, reqQty,
                List.of(), null, null, null);
    }
}
