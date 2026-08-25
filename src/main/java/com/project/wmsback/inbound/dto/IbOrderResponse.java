package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.IbPrgr;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 입고예정(ASN) 관리 목록 1행. 대시보드의 진행 분포도 이 응답을 쓴다.
 * <p>
 * QueryDSL Projections.constructor로 직접 채워지므로 생성자가 public이다.
 * 화면이 안 쓰는 값은 담지 않는다 — 검수 진행(라인 수)은 {@link IbOrderInspResponse},
 * 수량 합계 전량은 {@link IbOrderCfmResponse}가 가진다.
 */
@Getter
public class IbOrderResponse {

    private final Long ibOrderId;
    private final String ibNo;
    /**
     * 화면 표시용 5단계 진행 — 저장값이 아니라 수량·적치지시 존재에서 SQL CASE로 파생한다.
     * 저장 상태(IbStatus 3값)는 이 화면이 쓰지 않는다 — 뱃지도 검색도 이 값 하나로 한다.
     */
    private final IbPrgr prgr;
    private final String vndrNm;
    /** 반품이면 점포명, 아니면 null */
    private final String storeNm;
    private final String odrDvsn;
    private final LocalDate expctDe;
    /** 예정 수량 합계 (라인 파생) */
    private final long totalExpctQty;
    /**
     * 최종 검수일시 — 이 입고건 라인들의 검수일시 중 가장 늦은 것 (검수 전이면 null).
     * <p>
     * 헤더는 「얼마나 왔나」가 아니라 「언제 움직였나」를 든다. 수량 진행은 라인 그리드가 맡는다 —
     * 여러 상품이 섞인 헤더 합계는 단위가 EA밖에 될 수 없어 진행 파악에 도움이 안 되기 때문이다.
     * <p>
     * 최초가 아니라 최종인 이유는 {@code IbOrderRepositoryImpl#lastReceiveDt} 참고.
     */
    private final LocalDateTime inspDt;
    /** 확정일시 — 사람이 입고확정을 누른 시각(IbOrder#confirm만 채운다). 확정 전이면 null */
    private final LocalDateTime cfmDt;

    /**
     * prgr만 enum이 아니라 이름 문자열로 받는다 — 진행단계는 저장 컬럼이 아니라 SQL CASE의 결과라
     * 리터럴로 뽑을 수밖에 없다(사유는 {@code IbOrderRepositoryImpl#progressCode}).
     */
    public IbOrderResponse(Long ibOrderId, String ibNo, String prgr,
                           String vndrNm, String storeNm, String odrDvsn, LocalDate expctDe, long totalExpctQty,
                           LocalDateTime inspDt, LocalDateTime cfmDt) {
        this.ibOrderId = ibOrderId;
        this.ibNo = ibNo;
        this.prgr = IbPrgr.valueOf(prgr);
        this.vndrNm = vndrNm;
        this.storeNm = storeNm;
        this.odrDvsn = odrDvsn;
        this.expctDe = expctDe;
        this.totalExpctQty = totalExpctQty;
        this.inspDt = inspDt;
        this.cfmDt = cfmDt;
    }
}
