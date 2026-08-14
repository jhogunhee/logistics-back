package com.project.mdm.vendor.service;

/**
 * 벤더를 참조하는 앱이 스스로 신고하는 포트. {@link com.project.mdm.prod.service.ProdRefChecker}와
 * 같은 구조 — `mdm`은 자기 데이터를 누가 쓰는지 몰라야 하는데, 벤더 삭제 가드는 두 앱의 입고 문서
 * 참조를 봐야 한다(FK가 0건이라 DB가 막아주지 않고, flush에 맡기면 조용히 지워져
 * 주문이 없는 벤더를 가리키게 된다).
 * <p>
 * 구현체는 참조를 소유한 도메인에 하나씩 둔다 — `WmsIbVendorRefChecker`(입고예정 ASN) ·
 * `OmsIbVendorRefChecker`(OMS 입고주문). 순서가 필요하면 {@code @Order}를 붙인다.
 */
public interface VendorRefChecker {

    /**
     * 이 벤더를 참조 중이면 사용자에게 보일 이름("입고주문" 등), 아니면 null.
     * 몇 건인지는 필요 없다 — 삭제를 막을 이유 하나면 충분하다.
     */
    String findReference(Long vendorId);
}
