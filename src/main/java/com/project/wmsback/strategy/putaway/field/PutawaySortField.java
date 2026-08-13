package com.project.wmsback.strategy.putaway.field;

import com.project.wmsback.strategy.putaway.method.PutawayMethodContext;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;

/**
 * 적치 후보 정렬 기준 (meta 도메인 "putaway-loc"). 상수 추가 = 화면 선택지·저장 검증·실행에
 * 동시 반영 — 저장 검증(PtawyStgyService)과 실행(PutawayRecommendService)이 이 enum 하나를 본다.
 *
 * <p>null은 방향과 무관하게 맨 뒤다 (할당의 InvnSortField와 같은 규칙).
 */
public enum PutawaySortField {

    PIKNG_PRTY("피킹순위", ls -> ls.loc().getPikngPrty()),
    PTAWY_PRTY("적치순위", ls -> ls.loc().getPtawyPrty()),
    LOC_CD("로케이션코드", ls -> ls.loc().getLocCd());

    private final String label;
    private final Function<PutawayMethodContext.LocStock, Comparable<?>> extractor;

    <U extends Comparable<U>> PutawaySortField(String label,
                                               Function<PutawayMethodContext.LocStock, U> extractor) {
        this.label = label;
        this.extractor = extractor::apply;
    }

    public String label() {
        return label;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Comparator<PutawayMethodContext.LocStock> comparator(boolean asc) {
        Comparator base = asc ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.comparing(extractor, Comparator.nullsLast(base));
    }

    /** 저장 시 검증용 조회 — 없으면 empty를 돌려주고 저장 서비스가 거부 메시지를 만든다 (P2) */
    public static Optional<PutawaySortField> find(String code) {
        for (PutawaySortField field : values()) {
            if (field.name().equals(code)) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    /** 실행용 조회. 없으면 "저장된 정의가 배포본과 어긋남" — 정상 경로에서는 나올 수 없다 */
    public static PutawaySortField of(String code) {
        return find(code).orElseThrow(() -> new IllegalStateException(
                "저장된 정렬 기준이 배포본과 어긋납니다 — 미등록 적치 정렬 기준: " + code));
    }
}
