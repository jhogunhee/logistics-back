package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.PikngTaskStatus;

import java.time.LocalDate;

/**
 * 피킹지시·피킹 화면의 하단 1행 — 할당(=지시) 단위.
 *
 * <p>발행 전(PLANNED)에는 할당 행이 그대로 온다({@code taskId}·{@code srtSeq}·{@code status}는 null,
 * 정렬은 발행 시와 같은 순서 — 발행 미리보기다). 발행 후(ISSUED)에는 지시 스냅샷에서 온다 —
 * 완료된 지시는 재고 행(inv)이 삭제됐을 수 있어 alloc → inv 조인으로는 표시할 수 없다.
 */
public record PikngRowResponse(
        Long taskId,
        Integer srtSeq,
        Long allocId,
        String outbNo,
        String storeNm,
        String prodCd,
        String prodNm,
        String locCd,
        String lotNo,
        LocalDate expiryDt,
        long drctQty,
        long cmplQty,
        long remainQty,
        PikngTaskStatus status,
        /** 결품 수량 (결품 종결로 포기한 잔량). 종결되지 않은 지시는 null */
        Long shotgeQty,
        /** 결품 사유 코드. 채워져 있으면 결품 종결로 닫힌 지시다 — 전량 집품 DONE과 구분된다 */
        String shotgeRsnCd
) {
    public static PikngRowResponse of(Long taskId, Integer srtSeq, Long allocId, String outbNo, String storeNm,
                                      String prodCd, String prodNm, String locCd, String lotNo,
                                      LocalDate expiryDt, long drctQty, long cmplQty, PikngTaskStatus status,
                                      Long shotgeQty, String shotgeRsnCd) {
        return new PikngRowResponse(taskId, srtSeq, allocId, outbNo, storeNm, prodCd, prodNm,
                locCd, lotNo, expiryDt, drctQty, cmplQty, drctQty - cmplQty, status, shotgeQty, shotgeRsnCd);
    }
}
