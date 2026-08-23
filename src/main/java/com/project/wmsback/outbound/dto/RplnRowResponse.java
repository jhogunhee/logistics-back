package com.project.wmsback.outbound.dto;

import com.project.wmsback.inventory.entity.InvMovStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 보충지시 1행 = 짝 피킹지시 1행. 순번·출고번호는 피킹지시의 것이라 작업자가 피킹 리스트와 맞춰 볼 수 있다 */
public record RplnRowResponse(
        Long rplnTaskId,
        String invMovNo,
        Long pikngTaskId,
        Integer srtSeq,
        String outbNo,
        String storeNm,
        String prodCd,
        String prodNm,
        String lotNo,
        LocalDate expiryDt,
        String fromLocCd,
        String toLocCd,
        long qty,
        InvMovStatus status,
        LocalDateTime cmplDt
) {
}
