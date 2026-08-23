package com.project.wmsback.outbound.dto;

import com.project.wmsback.inventory.entity.InvMovStatus;
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
        String shotgeRsnCd,
        /**
         * 짝 보충지시 상태. null = 보충 없음(피킹존 할당). DIRECTED면 보충이 끝나야 집을 수 있고
         * 피킹 화면은 그 행을 체크하지 못하게 한다(서버 가드 선반영). 취소된 보충은 null로 본다
         */
        InvMovStatus rplnStatus,
        /** 짝 보충지시 번호 (보충 화면에서 찾는 열쇠). 보충 없으면 null */
        String rplnNo
) {
    public static PikngRowResponse of(Long taskId, Integer srtSeq, Long allocId, String outbNo, String storeNm,
                                      String prodCd, String prodNm, String locCd, String lotNo,
                                      LocalDate expiryDt, long drctQty, long cmplQty, PikngTaskStatus status,
                                      Long shotgeQty, String shotgeRsnCd, InvMovStatus rplnStatus, String rplnNo) {
        return new PikngRowResponse(taskId, srtSeq, allocId, outbNo, storeNm, prodCd, prodNm,
                locCd, lotNo, expiryDt, drctQty, cmplQty, drctQty - cmplQty, status, shotgeQty, shotgeRsnCd,
                rplnStatus, rplnNo);
    }
}
