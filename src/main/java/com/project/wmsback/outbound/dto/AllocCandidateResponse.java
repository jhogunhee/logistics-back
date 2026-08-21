package com.project.wmsback.outbound.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 수동할당 후보 재고 1행. 자동할당이 쓰는 후보와 같은 집합이고, 다른 것은
 * <b>잔여수명 미달을 걸러내지 않고 표시만 한다</b>는 점이다.
 *
 * <p>수동할당의 존재 이유가 예외 처리라 기준 미달 Lot도 고를 수 있어야 한다 —
 * 화면이 {@code lifeRate}·{@code lifePass}·{@code lifeRjctRsn}으로 경고를 띄우고 판단은 사람이 한다.
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
        /**
         * 잔여수명 판정 통과 여부 — <b>자동할당과 같은 기준</b>이다. 전략에 제약 슬롯이 있으면 그
         * 정의로, 없으면 점포 기준({@code store.outb_life_rate})으로 판정한다.
         * 미관리 Lot은 대상이 아니므로 true.
         */
        boolean lifePass,
        /** 미달 사유 (예: {@code 잔여수명 40.0% < 기준 60%}). 통과면 null — 화면이 적용된 기준을 그대로 보여준다 */
        String lifeRjctRsn,
        /**
         * 자동할당에서 이 재고가 속하는 재고위치 계층 순번. 전략에 계층이 없으면 1(전체가 한 계층),
         * <b>어느 계층에도 맞지 않으면 null</b> — 자동할당은 이 재고를 배정하지 않는다. 잔여수명과 별개의
         * 축이라 따로 내린다: 잔여수명은 초록인데 계층에 안 속하는 재고가 있다(「피킹존만」 계층 등).
         */
        Integer tierSeq,
        /** 속한 계층의 조건 한 줄 (예: {@code BIZ_DVSN IN [PIKNG]}). 계층이 없으면 "전체", 안 속하면 null */
        String tierCond
) {
}
