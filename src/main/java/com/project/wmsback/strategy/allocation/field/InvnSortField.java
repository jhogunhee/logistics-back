package com.project.wmsback.strategy.allocation.field;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;

/**
 * 재고 정렬 기준 (meta 도메인 "allocation-invn"). 상수 추가 = 화면 선택지·저장 검증·실행에 동시 반영.
 *
 * <p><b>null은 방향과 무관하게 항상 맨 뒤다.</b> 유통기한 미관리 Lot(날짜 3종이 NULL)이
 * 오름차순에서 맨 앞으로 와 최우선 할당 대상이 되는 것은 명백한 사고이고, 내림차순이라고 해서
 * 「값 없음」을 앞에 두고 싶은 경우도 없다. 현행 붙박이 정렬의 {@code expiryDt.asc().nullsLast()}가
 * 이 규칙의 원형이다.
 */
public enum InvnSortField {

    EXPIRY_DT("유통기한", AlocInvnCandidate::expiryDt),
    MFG_DT("제조일자", AlocInvnCandidate::mfgDt),
    RECEIPT_DT("입고일자", AlocInvnCandidate::receiptDt),
    LOC_PIKNG_PRTY("로케이션 피킹순위", AlocInvnCandidate::pikngPrty),
    LOC_CD("로케이션코드", AlocInvnCandidate::locCd),
    /** 가용수량. 내림차순으로 두면 큰 재고부터 소진해 로케이션 파편화를 줄인다 */
    AVAL_QTY("가용수량", AlocInvnCandidate::avalQty);

    private final String label;
    private final Function<AlocInvnCandidate, Comparable<?>> extractor;

    <U extends Comparable<U>> InvnSortField(String label, Function<AlocInvnCandidate, U> extractor) {
        this.label = label;
        this.extractor = extractor::apply;
    }

    public String label() {
        return label;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Comparator<AlocInvnCandidate> comparator(boolean asc) {
        Comparator base = asc ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.comparing(extractor, Comparator.nullsLast(base));
    }

    /** 저장 시 검증용 조회 — 없으면 empty를 돌려주고 저장 서비스가 거부 메시지를 만든다 (P2) */
    public static Optional<InvnSortField> find(String code) {
        for (InvnSortField field : values()) {
            if (field.name().equals(code)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    /** 실행용 조회. 없으면 "저장된 정의가 배포본과 어긋남" — 정상 경로에서는 나올 수 없다 */
    public static InvnSortField of(String code) {
        return find(code).orElseThrow(() -> new IllegalStateException(
                "저장된 전략 정의가 배포본과 어긋납니다 — 미등록 재고 정렬 기준: " + code));
    }
}
