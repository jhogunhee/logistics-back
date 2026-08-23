package com.project.wmsback.inventory.dto;

import com.project.mdm.prod.entity.TmpZon;

import java.time.LocalDate;
import java.util.List;

/**
 * 보충 대상 1건 = 화면 1행 — min 미달인 고정로케이션과 FEFO 추천 배정.
 * assignments는 추천값일 뿐 예약이 아니다(추천≠예약) — 발행 시 서버가 같은 식으로 재검증한다.
 * sources는 이 상품의 원천 후보 전체(추천에 안 쓰인 것 포함) — 화면의 원천 보정 콤보가 추가 왕복 없이 쓴다.
 */
public record SpmtTargetResponse(
        Long fxngLocId,
        Long locId,
        String locCd,
        String zonCd,
        Long prodId,
        String prodCd,
        String prodNm,
        TmpZon tmpZon,
        long minQty,
        long maxQty,
        long onHandQty,
        long inflowQty,
        long shortQty,
        List<Assignment> assignments,
        List<Source> sources
) {

    /** FEFO 추천 배정 1건 — 원천 재고 행에서 qty만큼 */
    public record Assignment(Long invId, String fromLocCd, String lotNo, LocalDate expiryDt,
                             long avalQty, long qty) {
    }

    /** 원천 후보 1건 (FEFO 순). avalQty는 다른 대상의 추천 배정을 빼지 않은 원값 */
    public record Source(Long invId, String fromLocCd, String lotNo, LocalDate expiryDt, long avalQty) {
    }
}
