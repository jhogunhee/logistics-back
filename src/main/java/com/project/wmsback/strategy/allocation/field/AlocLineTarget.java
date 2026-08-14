package com.project.wmsback.strategy.allocation.field;

import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbOrder;

import java.time.LocalDate;

/**
 * 할당 판정 대상 — 출고 라인 1건의 값 스냅샷.
 *
 * <p>엔티티가 아니라 값 레코드로 뽑는 이유는 웨이브와 같다: 산정을 순수 함수로 유지해
 * 실행·미리보기가 같은 함수를 공유하고(P4), 지연로딩이 산정 결과에 끼어들지 않게 하기 위함이다.
 *
 * <p>적용대상 판정(AlocTgtField) · 분배 대상 선별(AlocLineField) · 주문 정렬(OdrSortField)이
 * 전부 이 레코드를 본다 — 셋 다 「라인 하나의 성질」을 묻기 때문에 대상 타입을 나눌 이유가 없다.
 */
public record AlocLineTarget(
        Long outbLineId,
        Long outbOrderId,
        String outbNo,
        Long prodId,
        String prodCd,
        String storeCd,
        String storeNm,
        /** 점포그룹·점포유형 — 분배 대상 선별(AlocLineField)의 조건값. null = 미지정 (부정 연산자만 참) */
        String storeGrp,
        String storeTyp,
        /** 점포의 납품 허용 잔여수명 비율(%) — SHELF_LIFE_PCT의 basis=STORE가 쓰는 기준 */
        short outbLifeRate,
        String outbTyp,
        String vhclFltno,
        /** 출고예정일. 잔여수명의 기준일이다 — 점포가 요구하는 것은 「납품 시점의 잔여수명」 */
        LocalDate expctDe,
        long odrQty,
        /** 이번 실행 전까지의 기할당 합계 */
        long alocQty
) {

    /** 잔여요청 = 이 라인이 이번 실행에서 받을 수 있는 상한. 과할당 금지가 여기서 표현된다 */
    public long reqQty() {
        return Math.max(odrQty - alocQty, 0);
    }

    public static AlocLineTarget of(OutbLine line, long alocQty) {
        OutbOrder order = line.getOutbOrder();
        Short lifeRate = order.getStore().getOutbLifeRate();
        return new AlocLineTarget(
                line.getId(), order.getId(), order.getOutbNo(),
                line.getProd().getId(), line.getProd().getProdCd(),
                order.getStore().getStoreCd(), order.getStore().getStoreNm(),
                order.getStore().getStoreGrp(), order.getStore().getStoreTyp(),
                // 기준 미설정은 「요구 없음」이다 — 0%면 기한만 안 지났으면 전부 통과한다
                lifeRate != null ? lifeRate : 0,
                order.getOutbTyp(), order.getVhclFltno(), order.getExpctDe(),
                line.getOdrQty(), alocQty);
    }
}
