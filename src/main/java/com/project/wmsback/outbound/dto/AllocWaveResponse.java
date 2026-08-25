package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.WaveStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 할당 대상 웨이브 1행. 수량 셋은 이 웨이브에서 <b>아직 출고확정되지 않은 주문</b>의 라인 합계다 —
 * 확정된 주문의 할당 행은 확정 후에도 남으므로, 주문수량과 할당수량이 같은 모수를 봐야 잔량이 맞는다.
 *
 * <p>잔량이 남았거나({@code remainQty > 0}) <b>해제 가능한 할당이 남은</b> 웨이브가 목록에
 * 오른다 — 전량 할당된 웨이브도 지시가 전부 발행되기 전까지는 남아, 방금 한 할당의 확인과
 * 해제가 이 화면에서 끝난다. 실행(자동할당)은 잔량 있는 웨이브만 대상이다.
 *
 * <p>잔량 판정은 합계로 한다. 라인별로 과할당이 막혀 있어({@code SUM(aloc_qty) <= odr_qty})
 * 웨이브 합계의 잔량과 「잔량 있는 라인의 존재」가 정확히 같은 뜻이 된다 — 그래서 라인 단위
 * EXISTS 대신 합계로 판정할 수 있다.
 */
public record AllocWaveResponse(
        Long wavId,
        String wavNo,
        /**
         * 웨이브 상태. ISSUED도 대상에 오른다 — 결품 종결이 잔량을 사후에 키우거나, 지시취소 후
         * 할당해제로 할당이 0건이 된 주문이 있으면 그 웨이브를 다시 채워야 하기 때문이다.
         * 추가 할당분은 <b>지시를 다시 발행해야</b> 현장에 나가므로 화면이 이 값으로 구분해 안내한다.
         */
        WaveStatus status,
        Long wavStgyId,
        /** 웨이브의 출고예정일 — 편성 가드가 소속 주문의 출고예정일을 하나로 강제하므로 어느 주문의 값이든 같다 */
        LocalDate expctDe,
        LocalDateTime createdAt,
        long orderCount,
        long odrQty,
        long alocQty,
        long remainQty
) {
    public static AllocWaveResponse of(Long wavId, String wavNo, WaveStatus status, Long wavStgyId,
                                       LocalDate expctDe, LocalDateTime createdAt,
                                       long orderCount, long odrQty, long alocQty) {
        return new AllocWaveResponse(wavId, wavNo, status, wavStgyId, expctDe, createdAt,
                orderCount, odrQty, alocQty, odrQty - alocQty);
    }
}
