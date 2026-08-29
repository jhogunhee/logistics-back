package com.project.wmsback.worker.entity;

import com.project.wmsback.inventory.entity.RefDocTyp;
import com.project.wmsback.inventory.entity.TxTyp;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 작업자 실적의 작업 종류. 저장되는 값이 아니라 {@code inv_hist} 한 행의
 * {@code (tx_typ, rfn_doc_typ)} 조합에서 되읽는 파생 분류다 — 실적 전용 컬럼을 두지 않고
 * 원장을 그대로 실적으로 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum WrkrWorkTyp {

    RECEIVE("입고검수"),
    RECEIVE_CNCL("검수취소"),
    PUTAWAY("적치"),
    INV_MOV("재고이동"),
    RPLN("수시보충"),
    PICK("피킹"),
    SHIP("출고확정"),
    STKTK("재고조사"),
    INV_ADJ("재고조정"),
    LOT_CHNG("로트변경");

    private final String label;

    /**
     * 조합 → 작업 종류. 규칙표에 없는 조합은 {@code null}이다 — 조회가 이미 걸러내지만,
     * 나중에 새 조합이 생겼을 때 조용히 다른 종류에 섞이지 않게 여기서도 막는다.
     */
    public static WrkrWorkTyp of(TxTyp txTyp, RefDocTyp rfnDocTyp) {
        return switch (txTyp) {
            case RECEIVE -> RECEIVE;
            case PICK -> PICK;
            case SHIP -> SHIP;
            case RPLN -> RPLN;
            case MOVE -> rfnDocTyp == RefDocTyp.INBOUND ? PUTAWAY
                    : rfnDocTyp == RefDocTyp.INV_MOV ? INV_MOV
                    : null;
            case ADJUST -> rfnDocTyp == null ? null : switch (rfnDocTyp) {
                case INBOUND -> RECEIVE_CNCL;
                case INV_STKTK -> STKTK;
                case INV_ADJ -> INV_ADJ;
                case LOT_CHNG -> LOT_CHNG;
                default -> null;
            };
        };
    }
}
