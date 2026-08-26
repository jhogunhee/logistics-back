package com.project.omsback.inbound.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 자동발주 산정 결과 — 벤더 1곳 = 발주 1건이다.
 * <p>
 * 라인이 산식의 항을 전부 싣는 이유는 화면이 「왜 이만큼 시키라는 건지」를 그 자리에서 보여줘야 하기 때문이다.
 * 판정이 아니라 제안이라 저장되는 것은 없고, 사람이 수량을 고쳐 발행할 수 있다.
 *
 * @param expctDe 입고 예정일 = 오늘 + 라인 중 가장 긴 리드타임 (주문 헤더에 예정일이 하나뿐이라 최대를 쓴다)
 */
public record AtoOdrProposalResponse(Long vendorId, String vndrCd, String vndrNm,
                                     LocalDate expctDe, List<Line> lines) {

    /**
     * @param avalQty    가용 재고 (EA)
     * @param openAsnQty 미입고 입고예정 잔량 (EA)
     * @param openOdrQty 미확정 발주 수량 (입고단위 — 화면 표시용)
     * @param openOdrEaQty 그 발주 수량의 낱개 환산 (EA — 순재고에 더해지는 값)
     * @param netQty     순재고 = 가용 + 미입고 예정 + 미확정 발주 (EA)
     * @param shortEaQty 발주 상한 − 순재고 (EA)
     * @param odrQty     제안 발주 수량 (입고단위) — 올림 환산 후 최소주문수량 적용
     */
    public record Line(Long prodVndrId, Long prodId, String prodCd, String prodNm,
                       String inbUomCd, long eaPerUom,
                       long avalQty, long openAsnQty, long openOdrQty, long openOdrEaQty,
                       long netQty, long minQty, long maxQty, long shortEaQty,
                       long minOdrQty, int leadDays, long odrQty) {
    }
}
