package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.IbStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 입고검수 화면의 입고건 목록 1행. 검수정책 시뮬레이션의 입고 선택 목록도 이 응답을 쓴다.
 * <p>
 * <b>진행단계(IbPrgr)를 담지 않는다.</b> 이 화면은 「검수할 수 있는가」만 보므로 저장 상태 3값이면
 * 충분하고, 그 덕에 5단계 파생에 필요한 적치지시 EXISTS 서브쿼리를 통째로 건너뛴다.
 * <p>
 * 수량 합계도 담지 않는다 — 저장 단위가 낱개(EA)로 통일돼 합산 자체는 성립하지만, 생수 2박스와
 * 김밥 3개가 섞인 낱개 합계는 진행 파악에 도움이 안 된다. 수량은 단위와 함께 라인 그리드가 보여준다.
 */
@Getter
public class IbOrderInspResponse {

    private final Long ibOrderId;
    private final String ibNo;
    /** 저장 상태 3값 — 검수 가능 여부(SCHEDULED·RECEIVING)와 확정건 제외에 쓴다 */
    private final IbStatus status;
    private final String vndrNm;
    /** 반품이면 점포명, 아니면 null */
    private final String storeNm;
    private final String odrDvsn;
    private final LocalDate expctDe;
    /** 전체 라인 수 (저장값이 아니라 라인에서 파생) */
    private final int lineCount;
    /**
     * 전량 검수된 라인 수 (rcvdQty >= expctQty) — 부분 검수중인 라인은 세지 않는다.
     * 착수 기준(rcvdQty > 0)으로 세면 라인마다 1개씩만 검수해도 「전체 완료」로 보인다.
     * 화면은 이 둘을 「검수 진행 3 / 5」로 함께 보여준다.
     */
    private final int cmplLineCount;
    /** 최종 검수일시 — 검수중인 건이 여럿일 때 하다 만 건을 찾는 단서 (검수 전이면 null) */
    private final LocalDateTime inspDt;

    public IbOrderInspResponse(Long ibOrderId, String ibNo, IbStatus status,
                               String vndrNm, String storeNm, String odrDvsn, LocalDate expctDe,
                               int lineCount, int cmplLineCount, LocalDateTime inspDt) {
        this.ibOrderId = ibOrderId;
        this.ibNo = ibNo;
        this.status = status;
        this.vndrNm = vndrNm;
        this.storeNm = storeNm;
        this.odrDvsn = odrDvsn;
        this.expctDe = expctDe;
        this.lineCount = lineCount;
        this.cmplLineCount = cmplLineCount;
        this.inspDt = inspDt;
    }
}
