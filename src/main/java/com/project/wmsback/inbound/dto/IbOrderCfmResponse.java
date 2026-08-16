package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.IbPrgr;
import com.project.wmsback.inbound.entity.IbStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 입고확정 화면의 입고건 목록 1행.
 * <p>
 * 진행단계와 저장 상태를 <b>둘 다</b> 가진다 — 확정 가능 판정이 {@code status == RECEIVING &&
 * prgr == PTAWY_CMPL}이라 한쪽만으로는 안 된다.
 * <p>
 * <b>최종 검수일시는 담지 않는다.</b> 이 화면이 보는 것은 결품(예정−검수)과 미적치(검수−적치)뿐이라
 * inv_hist를 훑는 중첩 서브쿼리를 건너뛴다.
 */
@Getter
public class IbOrderCfmResponse {

    private final Long ibOrderId;
    private final String ibNo;
    /** 화면 표시용 5단계 진행 — 저장값이 아니라 SQL CASE 파생 */
    private final IbPrgr prgr;
    /** 저장 상태 3값 — prgr와 함께 확정 가능 여부를 판정한다 */
    private final IbStatus status;
    private final String vndrNm;
    private final LocalDate expctDe;
    /** 예정 수량 합계 — 화면이 결품(예정−검수)을 계산한다 */
    private final long totalExpctQty;
    /** 검수 수량 합계 — 결품과 미적치 양쪽에 쓰인다 */
    private final long totalRcvdQty;
    /** 적치 수량 합계 — 화면이 미적치(검수−적치)를 계산한다 */
    private final long totalPtawyQty;
    /** 확정일시 — 사람이 입고확정을 누른 시각. 확정 전이면 null */
    private final LocalDateTime cfmDt;

    /** prgr를 이름 문자열로 받는 이유는 {@code IbOrderRepositoryImpl#progressCode} 참고 */
    public IbOrderCfmResponse(Long ibOrderId, String ibNo, String prgr, IbStatus status,
                              String vndrNm, LocalDate expctDe,
                              long totalExpctQty, long totalRcvdQty, long totalPtawyQty,
                              LocalDateTime cfmDt) {
        this.ibOrderId = ibOrderId;
        this.ibNo = ibNo;
        this.prgr = IbPrgr.valueOf(prgr);
        this.status = status;
        this.vndrNm = vndrNm;
        this.expctDe = expctDe;
        this.totalExpctQty = totalExpctQty;
        this.totalRcvdQty = totalRcvdQty;
        this.totalPtawyQty = totalPtawyQty;
        this.cfmDt = cfmDt;
    }
}
