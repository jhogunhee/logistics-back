package com.project.wmsback.strategy.allocation.field;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;

/**
 * 주문(라인) 정렬 기준 (meta 도메인 "allocation-order"). 먼저 처리된 라인이 재고를 먼저
 * 가져가므로 <b>이 정렬이 곧 우선권</b>이다.
 *
 * <p>정렬은 <b>상품 그룹 안에서만</b> 일어난다. 그룹 순회 순서(prod_id ASC)는 정렬이 아니라
 * 그룹 간 데드락을 막는 락 순서라 전략이 건드리지 않는다.
 *
 * <p>null은 방향과 무관하게 맨 뒤다 (InvnSortField와 같은 규칙).
 */
public enum OdrSortField {

    EXPCT_DE("출고예정일", AlocLineTarget::expctDe),
    OUTB_NO("출고번호", AlocLineTarget::outbNo),
    STORE_CD("점포코드", AlocLineTarget::storeCd),
    /** 주문수량. 내림차순이면 큰 주문부터 채워 「완전 출고 주문 수」 대신 대형 주문을 우선한다 */
    ODR_QTY("주문수량", AlocLineTarget::odrQty);

    private final String label;
    private final Function<AlocLineTarget, Comparable<?>> extractor;

    <U extends Comparable<U>> OdrSortField(String label, Function<AlocLineTarget, U> extractor) {
        this.label = label;
        this.extractor = extractor::apply;
    }

    public String label() {
        return label;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Comparator<AlocLineTarget> comparator(boolean asc) {
        Comparator base = asc ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.comparing(extractor, Comparator.nullsLast(base));
    }

    /** 저장 시 검증용 조회 — 없으면 empty를 돌려주고 저장 서비스가 거부 메시지를 만든다 (P2) */
    public static Optional<OdrSortField> find(String code) {
        for (OdrSortField field : values()) {
            if (field.name().equals(code)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    /** 실행용 조회. 없으면 "저장된 정의가 배포본과 어긋남" — 정상 경로에서는 나올 수 없다 */
    public static OdrSortField of(String code) {
        return find(code).orElseThrow(() -> new IllegalStateException(
                "저장된 전략 정의가 배포본과 어긋납니다 — 미등록 주문 정렬 기준: " + code));
    }
}
