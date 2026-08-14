package com.project.mdm.store.service;

/**
 * 점포를 참조하는 앱이 스스로 신고하는 포트. {@link com.project.mdm.prod.service.ProdRefChecker}와
 * 같은 구조 — `mdm`은 자기 데이터를 누가 쓰는지 몰라야 하는데, 점포 삭제 가드는 두 앱의 출고주문
 * 참조를 봐야 한다(FK가 0건이라 DB가 막아주지 않고, 벤더처럼 flush에 맡기면 조용히 지워져
 * 주문이 없는 점포를 가리키게 된다).
 * <p>
 * 구현체는 참조를 소유한 도메인에 하나씩 둔다 — `WmsStoreRefChecker`(창고 출고주문) ·
 * `OmsOutbStoreRefChecker`(OMS 출고주문). 순서가 필요하면 {@code @Order}를 붙인다.
 */
public interface StoreRefChecker {

    /**
     * 이 점포를 참조 중이면 사용자에게 보일 이름("출고주문" 등), 아니면 null.
     * 몇 건인지는 필요 없다 — 삭제를 막을 이유 하나면 충분하다.
     */
    String findReference(Long storeId);
}
