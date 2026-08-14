package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.entity.IbPrgr;
import com.project.wmsback.inbound.entity.IbStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class IbOrderResponse {

    private final Long ibOrderId;
    private final String ibNo;
    private final IbStatus status;
    /**
     * 화면 표시용 5단계 진행 — 저장값이 아니라 수량·적치지시 존재에서 파생(IbOrder#progress).
     * status(저장 3값)는 워크플로 사건만 담고, 목록이 보여줄 「어디까지 왔나」는 이 값이 담당한다.
     */
    private final IbPrgr prgr;
    private final String vndrNm;
    private final LocalDate expctDe;
    /** 전체 라인 수 (저장값이 아니라 라인에서 파생) */
    private final int lineCount;
    /**
     * 전량 검수된 라인 수 (rcvdQty >= expctQty) — 부분 검수중인 라인은 세지 않는다.
     * 착수 기준(rcvdQty > 0)으로 세면 라인마다 1개씩만 검수해도 「전체 완료」로 보인다.
     */
    private final int cmplLineCount;
    /** 예정 수량 합계 (라인 파생) */
    private final long totalExpctQty;
    /** 검수 수량 합계 (라인 파생) */
    private final long totalRcvdQty;
    /** 적치 수량 합계 (라인 파생) — 화면이 적치 잔량(검수-적치)을 계산할 때 쓴다 */
    private final long totalPtawyQty;
    /**
     * 최종 검수일시 — 이 입고건 라인들의 검수일시 중 가장 늦은 것 (검수 전이면 null).
     * <p>
     * 헤더는 「얼마나 왔나」가 아니라 「언제 움직였나」를 든다. 수량 진행은 라인 그리드가 맡는다 —
     * 여러 상품이 섞인 헤더 합계는 단위가 EA밖에 될 수 없어 진행 파악에 도움이 안 되기 때문이다.
     * <p>
     * 최초가 아니라 최종인 이유는 {@code IbOrderRepositoryCustom#lastReceiveDtByLine} 참고.
     */
    private final LocalDateTime inspDt;
    /** 확정일시 — 사람이 입고확정을 누른 시각(IbOrder#confirm만 채운다). 확정 전이면 null */
    private final LocalDateTime cfmDt;
    private final LocalDateTime createdAt;

    private IbOrderResponse(IbOrder order, LocalDateTime inspDt, boolean hasOpenPtawyDrct) {
        this.ibOrderId = order.getId();
        this.ibNo = order.getIbNo();
        this.status = order.getStatus();
        this.prgr = order.progress(hasOpenPtawyDrct);
        this.vndrNm = order.getVendor().getVndrNm();
        this.expctDe = order.getExpctDe();
        this.lineCount = order.getLines().size();
        this.cmplLineCount = (int) order.getLines().stream()
                .filter(l -> l.getRcvdQty() >= l.getExpctQty()).count();
        this.totalExpctQty = order.getLines().stream().mapToLong(IbLine::getExpctQty).sum();
        this.totalRcvdQty = order.getLines().stream().mapToLong(IbLine::getRcvdQty).sum();
        this.totalPtawyQty = order.getLines().stream().mapToLong(IbLine::getPtawyQty).sum();
        this.inspDt = inspDt;
        this.cfmDt = order.getCfmDt();
        this.createdAt = order.getCreatedAt();
    }

    /** 최종 검수일시(inv_hist 집계)와 미완료 지시 존재는 라인에서 파생되지 않는다 — 서비스가 구해 넘긴다 */
    public static IbOrderResponse of(IbOrder order, LocalDateTime inspDt, boolean hasOpenPtawyDrct) {
        return new IbOrderResponse(order, inspDt, hasOpenPtawyDrct);
    }
}
