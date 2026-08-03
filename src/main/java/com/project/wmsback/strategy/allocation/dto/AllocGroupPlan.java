package com.project.wmsback.strategy.allocation.dto;

import java.util.List;
import java.util.Map;

/**
 * 상품 그룹 1건의 산정 결과. <b>상태 변경이 아니라 계획</b>이다 —
 * 실전은 이 계획을 예약으로 반영하고, 미리보기는 그대로 화면에 내린다 (P4).
 *
 * <p>{@code trace}가 「이 라인이 왜 이만큼만 받았는지」를 담는다. 결품 테이블이 없는
 * 이 프로젝트에서 미충족의 근거는 여기와 실행 로그의 {@code dcsn_trc} 뿐이다.
 */
public record AllocGroupPlan(
        Long prodId,
        String prodCd,
        long reqQty,
        long asgnQty,
        List<LinePlan> lines,
        Map<String, Object> trace
) {

    public long shortQty() {
        return reqQty - asgnQty;
    }

    /** 라인 1건의 배정 결과 */
    public record LinePlan(
            Long outbLineId,
            String outbNo,
            String storeCd,
            String prodCd,
            long reqQty,
            long asgnQty,
            List<Assignment> assignments,
            /** 이 라인에서 후보가 빠진 사유 — 조용히 사라지는 재고를 만들지 않기 위한 근거 */
            List<Skip> skips
    ) {
        public long shortQty() {
            return reqQty - asgnQty;
        }
    }

    /** 재고 1건에서 가져간 수량 */
    public record Assignment(
            Long invId,
            String locCd,
            String lotNo,
            long qty
    ) {
    }

    /**
     * 후보에서 제외된 재고 1건과 사유. <b>(재고, 라인) 조합마다</b> 생긴다 —
     * 같은 Lot이 A점포엔 통과하고 B점포엔 걸릴 수 있어서 사유가 라인에 매인다.
     */
    public record Skip(
            Long invId,
            String locCd,
            String lotNo,
            String reason
    ) {
    }
}
