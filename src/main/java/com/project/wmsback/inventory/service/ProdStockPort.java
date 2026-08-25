package com.project.wmsback.inventory.service;

import java.util.Collection;
import java.util.Map;

/**
 * 창고가 상품별 재고 현황을 밖에 알려주는 <b>읽기 전용</b> 포트.
 * <p>
 * {@code omsback → wmsback} 의존은 원래 작업문서 둘({@code IbOrder} · {@code OutbOrder})로 제한돼 있는데,
 * 자동발주 산정은 「이 상품이 창고에 얼마나 있나」를 알아야 발주 여부를 정할 수 있다. 문서를 만들려고
 * 재고 테이블을 직접 조회하면 omsback이 창고 내부 구조(로케이션 · 존 · 반품존 판정)에 결합되므로,
 * 질문 하나를 포트로 세워 창고가 답한다. <b>쓰기는 없다</b> — 재고를 건드리는 창구는 여전히 {@code InvStore} 하나다.
 *
 * @see com.project.wmsback.inventory.repository.ProdStockQueryRepository 구현
 */
public interface ProdStockPort {

    /**
     * @param avalQty    가용 합계(보유 − 예약 − 보류). 반품존 재고는 뺀다 — 불량으로 받아 보류에 묶인 물건이라
     *                   발주를 미룰 근거가 되지 못한다
     * @param openAsnQty 아직 입고확정되지 않은 입고예정의 잔량 합계(예정 − 검수 − 불량). 반품 ASN은 뺀다
     */
    record ProdStock(Long prodId, long avalQty, long openAsnQty) {

        public long total() {
            return avalQty + openAsnQty;
        }
    }

    /** 상품별 현황. 재고도 예정도 없는 상품은 맵에 없다 — 호출측이 0으로 본다 */
    Map<Long, ProdStock> stockByProd(Collection<Long> prodIds);
}
