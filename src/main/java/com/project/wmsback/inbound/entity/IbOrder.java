package com.project.wmsback.inbound.entity;

import com.project.wmsback.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 입고예정(ASN) 헤더. 부분입고 여부는 상태가 아니라 라인 수량(expct vs rcvd)에서 파생.
 */
@Entity
@Table(name = "ib_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IbOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ib_order_id")
    private Long id;

    /** 입고 번호 (업무 식별자, 예: IB-20260714-001) */
    @Column(name = "ib_no", nullable = false, length = 30, unique = true)
    private String ibNo;

    /** 워크플로 상태 (부분입고 상태 없음 — 라인 수량에서 파생) */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private IbStatus status;

    /** 납품 벤더명 (v1은 벤더 마스터 없이 텍스트 보관) */
    @Column(name = "vndr_nm", nullable = false, length = 100)
    private String vndrNm;

    /** 입고 예정일 */
    @Column(name = "expct_dt", nullable = false)
    private LocalDate expctDt;

    /** 입고 마감(close) 시각. 마감은 미입고 잔량을 확정하는 명시적 액션 */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @OneToMany(mappedBy = "ibOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IbLine> lines = new ArrayList<>();

    @Builder
    private IbOrder(String ibNo, String vndrNm, LocalDate expctDt) {
        this.ibNo = ibNo;
        this.vndrNm = vndrNm;
        this.expctDt = expctDt;
        this.status = IbStatus.SCHEDULED;
    }

    public void addLine(IbLine line) {
        lines.add(line);
        line.assignOrder(this);
    }

    /** 취소. 검수가 시작되면(SCHEDULED 이후) 불가 */
    public void cancel() {
        if (status != IbStatus.SCHEDULED) {
            throw new IllegalStateException("검수가 시작된 입고는 취소할 수 없습니다: " + ibNo);
        }
        this.status = IbStatus.CANCELLED;
    }

    /** 검수 가능 상태 검증 + 첫 검수 시 SCHEDULED → RECEIVING 전이 */
    public void startReceiving() {
        if (status == IbStatus.SCHEDULED) {
            this.status = IbStatus.RECEIVING;
            return;
        }
        if (status != IbStatus.RECEIVING) {
            throw new IllegalStateException("검수할 수 없는 상태입니다 (" + status.getLabel() + "): " + ibNo);
        }
    }

    /** 입고 마감. 검수가 시작된(RECEIVING) 입고만 가능 — 잔량(예정-검수)은 미입고로 확정된다 */
    public void close() {
        if (status != IbStatus.RECEIVING) {
            throw new IllegalStateException("검수가 시작된 입고만 마감할 수 있습니다 (" + status.getLabel() + "): " + ibNo);
        }
        transitionToReceived();
    }

    /**
     * 검수 저장 시점마다 호출. 전 라인이 전량 검수(rcvdQty >= expctQty)됐으면
     * 명시적 마감(close) 없이 바로 RECEIVING → RECEIVED로 전이한다.
     * close()는 그 반대(더 안 오는 잔량을 미입고로 확정)로 끝내는 경우에만 쓰는 명시적 액션.
     */
    public void checkAndAutoReceive() {
        if (status != IbStatus.RECEIVING) {
            return;
        }
        if (allLinesFullyReceived()) {
            transitionToReceived();
        }
    }

    private boolean allLinesFullyReceived() {
        return lines.stream().allMatch(l -> l.getRcvdQty() >= l.getExpctQty());
    }

    private void transitionToReceived() {
        this.status = IbStatus.RECEIVED;
        this.closedAt = LocalDateTime.now();
        checkAndComplete(); // 이미 전량 적치돼 있었다면(적치는 마감과 무관하게 가능) 바로 COMPLETED
    }

    /**
     * RECEIVED 상태이고 전 라인이 전량 적치(putawayQty == rcvdQty)됐으면 COMPLETED로 전이한다.
     * 적치는 마감 여부와 무관하게 즉시 가능하므로, 마감(close)과 적치(putaway) 양쪽에서
     * 각자 끝나는 시점에 이 메서드를 호출해 조건 충족 여부를 확인한다.
     */
    public void checkAndComplete() {
        if (status != IbStatus.RECEIVED) {
            return;
        }
        boolean allPutaway = lines.stream().allMatch(l -> l.getPtwyQty().equals(l.getRcvdQty()));
        if (allPutaway) {
            this.status = IbStatus.COMPLETED;
        }
    }

    /**
     * 검수 취소로 전량검수 상태가 깨졌으면 자동 마감(RECEIVED)을 되돌린다.
     * 이게 없으면 RECEIVED로 자동 전이된 뒤 그 전이를 만든 검수 건을 취소했을 때,
     * 상태는 계속 RECEIVED인데 rcvdQty < expctQty가 되어 startReceiving()이 막혀
     * 남은 수량을 다시는 검수할 수 없는 상태로 고착된다.
     */
    public void reopenIfNoLongerFullyReceived() {
        if (status == IbStatus.RECEIVED && !allLinesFullyReceived()) {
            this.status = IbStatus.RECEIVING;
            this.closedAt = null;
        }
    }
}
