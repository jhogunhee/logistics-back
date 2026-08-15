package com.project.mdm.prod.service;

/**
 * 상품을 참조하는 앱이 스스로 신고하는 포트.
 * <p>
 * `mdm`은 자기 데이터를 누가 쓰는지 몰라야 한다 — `wmsback`·`omsback`이 `mdm`을 import하지
 * 그 반대가 아니다. 그런데 상품 삭제 가드는 두 앱의 참조를 모두 봐야 한다(FK가 0건이라 DB가
 * 막아주지 않는다). 그래서 방향을 뒤집는다: 참조하는 쪽이 이 인터페이스를 구현해 빈으로
 * 등록하고, {@link ProdService}가 등록된 구현체를 순회한다.
 * <p>
 * 구현체는 참조를 소유한 도메인에 하나씩 둔다 — `WmsProdRefChecker`(재고 · 이력 · 입고예정 ·
 * 출고주문 · Lot)와 `OmsIbProdRefChecker`(입고주문) · `OmsOutbProdRefChecker`(출고주문).
 * WMS가 다섯 도메인을 한 구현체로 묶은 것은 Lot 하나에 딸린 조회들이라서고, OMS는 주문 원장이
 * 입고·출고로 갈려 있어 각 패키지가 자기 것만 신고한다. 순서가 필요하면 {@code @Order}를 붙인다.
 * <p>
 * 포장({@code prod_uom})은 어느 구현체도 세지 않는다 — 상품에 종속된 것이라 함께 지우는 게
 * 맞고, {@code Prod.uoms}의 cascade가 처리한다.
 * <p>
 * 상품에 대한 질문이 늘면 <b>인터페이스를 새로 만들지 말고 여기에 default 메서드로 더한다</b> —
 * 질문마다 포트를 파면 가드 하나에 인터페이스 1 + 구현체 N이 따라붙는다. 규칙은
 * "마스터 엔티티당 포트 1개, 참조하는 도메인당 구현체 1개"로 고정하고, 구현체는 자기가
 * 답할 수 있는 질문만 override한다.
 */
public interface ProdRefChecker {

    /**
     * 이 상품을 참조 중이면 사용자에게 보일 이름("재고" · "입고주문" 등), 아니면 null.
     * 상품 삭제 가드가 쓴다.
     */
    String findReference(Long prodId);

    /**
     * 입고단위 환산이 <b>앞으로 실행될</b> 문서가 있으면 그 이름, 없으면 null — 단위 변경
     * <p>
     * {@link #findReference}(참조가 하나라도 있으면 막음)와 달리 환산이 끝나지 않은 문서만 본다.
     */
    default String findOpenInbRef(Long prodId) {
        return null;
    }

    /** 출고단위 환산이 <b>앞으로 실행될</b> 문서가 있으면 그 이름, 없으면 null ({@link #findOpenInbRef} 참고) */
    default String findOpenOutbRef(Long prodId) {
        return null;
    }
}
