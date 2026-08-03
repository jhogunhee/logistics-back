package com.project.wmsback.outbound.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 수동할당 후보 재고 1행. 자동할당이 쓰는 후보와 같은 집합이고, 다른 것은
 * <b>잔여수명 미달을 걸러내지 않고 표시만 한다</b>는 점이다.
 *
 * <p>수동할당의 존재 이유가 예외 처리라 기준 미달 Lot도 고를 수 있어야 한다 —
 * 화면이 {@code lifeRate}·{@code lifePass}로 경고를 띄우고 판단은 사람이 한다.
 * 다만 <b>기한이 지난 Lot은 여기에 오지 않는다</b>(서비스가 제외 — 비율과 무관한 하드 가드).
 */
public record AllocCandidateResponse(
        Long invId,
        Long locId,
        String locCd,
        Long lotId,
        String lotNo,
        LocalDate mfgDt,
        LocalDate expiryDt,
        long onHandQty,
        long avalQty,
        /** 출고예정일 기준 잔여수명 비율(%). 유통기한 미관리 Lot이면 NULL */
        BigDecimal lifeRate,
        /** 점포 기준({@code store.outb_life_rate}) 통과 여부. 미관리 Lot은 대상이 아니므로 true */
        boolean lifePass
) {
}
