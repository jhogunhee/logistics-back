package com.project.omsback.inbound.service;

/**
 * 자동발주 수량 계산 — 순수 함수만 모은 곳. DB도 시각도 보지 않아 단위 테스트가 이 클래스에 붙는다.
 * <p>
 * <b>순재고 = 가용 + 미입고 예정 + 미확정 발주.</b> 세 항을 다 세는 것이 중복 발주를 막는 장치다 —
 * 한 발주 건은 어느 시점이든 셋 중 정확히 하나에만 산다(미확정 발주 → 확정하면 입고예정 → 검수하면 재고).
 * 겹치는 구간이 없어 어제 낸 발주가 오늘 순재고에 들어 있고, 그래서 「발주됨」 플래그가 필요 없다.
 */
public final class AtoOdrQtyCalc {

    private AtoOdrQtyCalc() {
    }

    /** 순재고 (EA) */
    public static long net(long avalQty, long openAsnQty, long openOdrEaQty) {
        return avalQty + openAsnQty + openOdrEaQty;
    }

    /** 발주 대상 판정. 순재고가 발주점 <b>미만</b>일 때만 — 같으면 아직 아니다 */
    public static boolean isShort(long net, long minQty) {
        return net < minQty;
    }

    /**
     * 발주 수량(입고단위). 상한까지 채울 낱개를 입고단위로 <b>올림</b> 환산하고 최소주문수량을 적용한다.
     * <p>
     * 올림인 이유는 내림이면 상한에 영영 못 닿기 때문이다(BOX 24개들이에 25개 부족 → 1박스면 1개 모자란다).
     * 최소주문수량이 상한을 넘겨도 그대로 낸다 — 상한으로 자르면 「최소」의 뜻이 없어진다.
     *
     * @param shortEaQty 상한 − 순재고 (0 이하면 발주 없음)
     * @param eaPerUom   입고단위 1개가 낱개 몇 개인지
     * @param minOdrQty  최소주문수량 (입고단위)
     */
    public static long proposedQty(long shortEaQty, long eaPerUom, long minOdrQty) {
        if (shortEaQty <= 0) {
            return 0;
        }
        return Math.max(ceilDiv(shortEaQty, eaPerUom), minOdrQty);
    }

    /** 올림 나눗셈. 두 값 모두 양수인 자리에서만 쓴다 */
    public static long ceilDiv(long dividend, long divisor) {
        if (divisor < 1) {
            throw new IllegalArgumentException("입고단위 낱개수량은 1 이상이어야 합니다: " + divisor);
        }
        return (dividend + divisor - 1) / divisor;
    }
}
