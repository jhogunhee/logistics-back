package com.project.wmsback.strategy.wave.field;

import com.project.wmsback.outbound.entity.OutbOrder;

import java.time.LocalDate;

/**
 * 웨이브 편성 판정 대상 — 출고 주문 1건의 값 스냅샷.
 * 엔티티가 아니라 값 레코드로 뽑는 이유: 판정을 순수 함수로 유지해 실행·미리보기가 같은 함수를
 * 공유하고(P4), 지연로딩이 판정 결과에 끼어들지 않게 하기 위함이다.
 *
 * <p>판정에 쓰이는 값은 {@code outbTyp}·{@code vhclFltno} 둘뿐이고, 나머지는 화면·로그에
 * 「어느 주문이 왜 편입/제외됐는지」를 보여주기 위한 표시용이다.
 */
public record WaveOrderTarget(
        Long outbOrderId,
        String outbNo,
        String outbTyp,
        String vhclFltno,
        String storeCd,
        String storeNm,
        LocalDate expctDe
) {

    public static WaveOrderTarget from(OutbOrder order) {
        return new WaveOrderTarget(order.getId(), order.getOutbNo(),
                order.getOutbTyp(), order.getVhclFltno(),
                order.getStore().getStoreCd(), order.getStore().getStoreNm(), order.getExpctDe());
    }
}
