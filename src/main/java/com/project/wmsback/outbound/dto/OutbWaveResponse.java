package com.project.wmsback.outbound.dto;

import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.WaveStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 출고 웨이브 1건. PK 키 이름은 {@code wavId}다 — 주문 응답({@code OutbOrderResponse.wavId})과
 * 컨트롤러 경로변수가 이미 그 이름이라, 웨이브 응답만 {@code outbWaveId}로 내려가면 프론트가 같은
 * 값을 응답마다 다른 키로 읽어야 한다.
 */
@Getter
public class OutbWaveResponse {

    private final Long wavId;
    private final String wavNo;
    private final WaveStatus status;
    /** 편성된 주문 수 (orders 매핑에서 파생) */
    private final int orderCount;
    /**
     * 할당이 시작된(CREATED가 아닌) 주문 수 (orders 매핑에서 파생). 0보다 크면 그 주문은 웨이브에서
     * 뺄 수 없고 웨이브도 삭제할 수 없다 — 화면이 눌러보기 전에 알 수 있게 목록에 실어 내린다.
     * 웨이브 상태는 할당을 기록하지 않으므로(PLANNED → ISSUED 둘뿐) 주문 상태에서 세는 것이 맞다.
     */
    private final int alocStartedCount;
    /**
     * 웨이브의 출고예정일 (orders 매핑에서 파생 — 빈 웨이브면 NULL). 편성 가드가 소속 주문의
     * 출고예정일을 하나로 강제하므로 어느 주문의 값이든 같다. 화면은 이 값으로 담기 후보를 거른다.
     */
    private final LocalDate expctDe;
    /** 피킹지시 발행 시각. 미발행(PLANNED)이면 NULL */
    private final LocalDateTime issuedDt;
    /** 종료 시각 — 소속 주문이 전부 출고확정된 순간. 미종료면 NULL */
    private final LocalDateTime closDt;
    /** 이 웨이브를 만든 전략 (NULL = 수동 생성) */
    private final Long wavStgyId;
    private final Long rvsnNo;
    private final LocalDateTime createdAt;

    private OutbWaveResponse(OutbWave wave) {
        this.wavId = wave.getId();
        this.wavNo = wave.getWavNo();
        this.status = wave.getStatus();
        this.orderCount = wave.getOrders().size();
        this.alocStartedCount = (int) wave.getOrders().stream()
                .filter(o -> o.getStatus() != OutbStatus.CREATED).count();
        this.expctDe = wave.getOrders().stream()
                .map(OutbOrder::getExpctDe).findFirst().orElse(null);
        this.issuedDt = wave.getIssuedDt();
        this.closDt = wave.getClosDt();
        this.wavStgyId = wave.getWavStgyId();
        this.rvsnNo = wave.getRvsnNo();
        this.createdAt = wave.getCreatedAt();
    }

    public static OutbWaveResponse from(OutbWave wave) {
        return new OutbWaveResponse(wave);
    }
}
