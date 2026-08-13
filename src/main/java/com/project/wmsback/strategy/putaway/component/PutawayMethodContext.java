package com.project.wmsback.strategy.putaway.component;

import com.project.wmsback.warehouse.entity.Loc;
import com.project.mdm.prod.entity.Prod;

import java.util.List;

/**
 * 추천 방식 입력 — 상품 온도대 일치 + STORAGE 로케이션 전체의 재고 현황 (불변 전제가
 * 이미 적용된 모집단). 방식은 이 안에서 후보를 고르기만 한다.
 */
public record PutawayMethodContext(Prod prod, List<LocStock> storageLocs) {

    /**
     * 로케이션 1개의 재고 현황. 로케이션 조건(PutawayLocField)의 판정 대상이기도 하다.
     * @param occupiedQty 이 로케이션의 전체 상품 보유 수량 합 (점유)
     * @param hasProd     같은 상품 재고(on_hand>0)가 이미 있는지
     * @param bizDvsn     소속 존의 업무유형 (zon.biz_dvsn — BizDvsn enum name). 존 미등록이면 null
     */
    public record LocStock(Loc loc, long occupiedQty, boolean hasProd, String bizDvsn) {
    }
}
