package com.project.mdm.prod.service;

/**
 * 상품을 참조하는 앱이 스스로 신고하는 포트.
 * <p>
 * `mdm`은 자기 데이터를 누가 쓰는지 몰라야 한다 — `wmsback`·`omsback`이 `mdm`을 import하지
 * 그 반대가 아니다. 그런데 상품 삭제 가드는 두 앱의 참조를 모두 봐야 한다(FK가 0건이라 DB가
 * 막아주지 않는다). 그래서 방향을 뒤집는다: 참조하는 쪽이 이 인터페이스를 구현해 빈으로
 * 등록하고, {@link ProdService}가 등록된 구현체를 순회한다.
 * <p>
 * 구현체는 앱당 하나다 — `WmsProdRefChecker`(재고 · 이력 · 입고예정 · 출고주문 · Lot)와
 * `OmsIbProdRefChecker`(입고주문). 순서가 필요하면 {@code @Order}를 붙인다.
 * <p>
 * 포장({@code prod_uom})은 어느 구현체도 세지 않는다 — 상품에 종속된 것이라 함께 지우는 게
 * 맞고, {@code Prod.uoms}의 cascade가 처리한다.
 */
public interface ProdRefChecker {

    /**
     * 이 상품을 참조 중이면 사용자에게 보일 이름("재고" · "입고주문" 등), 아니면 null.
     * 몇 건인지는 필요 없다 — 삭제를 막을 이유 하나면 충분하다.
     */
    String findReference(Long prodId);
}
