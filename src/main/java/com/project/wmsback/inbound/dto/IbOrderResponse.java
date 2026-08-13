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
    /** 검수 수량 합계 (라인 파생). 화면은 이 값을 직접 쓰지 않고 잔량·미적치의 재료로 쓴다 */
    private final long totalRcvdQty;
    /** 적치 수량 합계 (라인 파생). 미적치(= 검수 − 적치, 스테이징 잔류분)를 화면이 여기서 뺀다 */
    private final long totalPtawyQty;
    private final LocalDateTime createdAt;

    private IbOrderResponse(IbOrder order) {
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
        this.totalPtawyQty = order.getLines().stream().mapToLong(IbLine::getPtawyQty).sum();
        this.createdAt = order.getCreatedAt();
    }

    public static IbOrderResponse from(IbOrder order) {
        return new IbOrderResponse(order);
    }
}
