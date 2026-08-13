package com.project.wmsback.inbound.dto;

import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.entity.IbStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class IbOrderResponse {

    private final Long ibOrderId;
    private final String ibNo;
    private final IbStatus status;
    private final String vndrNm;
    private final LocalDate expctDe;
    /** 전체 라인 수 (저장값이 아니라 라인에서 파생) */
    private final int lineCount;
    /**
     * 전량 검수된 라인 수 (rcvdQty >= expctQty) — 부분 검수중인 라인은 세지 않는다.
     * 착수 기준(rcvdQty > 0)으로 세면 라인마다 1개씩만 검수해도 「전체 완료」로 보인다.
     * 비교 기준은 {@link IbOrder} 의 전량검수 판정과 같아야 한다 — 어긋나면 화면에
     * 「진행 5/5인데 상태는 검수중」이 나온다.
     */
    private final int cmplLineCount;
    /** 예정 수량 합계 (라인 파생) */
    private final long totalExpctQty;
    /** 검수 수량 합계 (라인 파생) */
    private final long totalRcvdQty;
    /**
     * 최종 검수일시 — 이 입고건 라인들의 검수일시 중 가장 늦은 것 (검수 전이면 null).
     * <p>
     * 헤더는 「얼마나 왔나」가 아니라 「언제 움직였나」를 든다. 수량 진행은 라인 그리드가 맡는다 —
     * 여러 상품이 섞인 헤더 합계는 단위가 EA밖에 될 수 없어 진행 파악에 도움이 안 되기 때문이다.
     * <p>
     * 최초가 아니라 최종인 이유는 {@code IbOrderRepositoryCustom#lastReceiveDtByLine} 참고.
     */
    private final LocalDateTime inspDt;
    /**
     * 확정일시 — 미입고 잔량이 확정된 시각. 지금은 전량 검수 시 자동 전이도 이 값을 채운다.
     * 「사람이 입고확정을 누른 시각」이 되려면 미구현 「입고확정」 화면과 상태 모델 변경
     * (자동 전이 제거)이 함께 와야 한다.
     */
    private final LocalDateTime cfmDt;
    private final LocalDateTime createdAt;

    private IbOrderResponse(IbOrder order, LocalDateTime inspDt) {
        this.ibOrderId = order.getId();
        this.ibNo = order.getIbNo();
        this.status = order.getStatus();
        this.vndrNm = order.getVendor().getVndrNm();
        this.expctDe = order.getExpctDe();
        this.lineCount = order.getLines().size();
        this.cmplLineCount = (int) order.getLines().stream()
                .filter(l -> l.getRcvdQty() >= l.getExpctQty()).count();
        this.totalExpctQty = order.getLines().stream().mapToLong(IbLine::getExpctQty).sum();
        this.totalRcvdQty = order.getLines().stream().mapToLong(IbLine::getRcvdQty).sum();
        this.inspDt = inspDt;
        this.cfmDt = order.getCfmDt();
        this.createdAt = order.getCreatedAt();
    }

    /** 최종 검수일시는 라인에서 파생되지 않는다 (inv_hist 집계) — 서비스가 구해 넘긴다 */
    public static IbOrderResponse of(IbOrder order, LocalDateTime inspDt) {
        return new IbOrderResponse(order, inspDt);
    }
}
