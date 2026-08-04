package com.project.wmsback.inbound.entity;

import com.project.common.entity.BaseEntity;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Lot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 적치지시 (스테이징 → 보관 2단계 적치: 지시=예약 → 실행=실물 MOVE).
 * 생성 시 스테이징 재고의 aloc_qty를 예약하고, 실행이 inv_hist MOVE 2행을 남기며 예약을 소진한다.
 * 실적 테이블은 따로 없다 — 부분 실행 실적은 inv_hist에 실행 횟수만큼 쌓인다 (rfn_doc_no = 입고번호).
 * from_loc은 항상 RCV-STAGE라 컬럼으로 두지 않는다.
 * <p>
 * 지시는 권고가 아니라 명령: 지시 TO와 다른 로케이션으로 적치할 수 없고, 다른 곳에 두려면 취소 후 재지시한다.
 * 이동지시(InvMovTask)와 달리 <b>잔량 취소가 없다</b> — 실행 실적이 하나라도 있으면 취소 자체를 막는다
 * (docs/design.md 「적치 지시」: cmpl_qty = 0인 지시만 취소 가능).
 */
@Entity
@Table(name = "putaway_task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PutawayTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "putaway_task_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ib_line_id", nullable = false)
    private IbLine ibLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    /** 적치할 보관 로케이션. 용량 계산에서 이 로케이션의 미완료 잔량이 유입분으로 잡힌다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_loc_id", nullable = false)
    private Loc toLoc;

    /** 지시 수량. 생성 시 이 수량만큼 스테이징 재고를 예약한다 */
    @Column(name = "drct_qty", nullable = false)
    private Long drctQty;

    /** 실행(실물 MOVE) 완료 수량 누계. 부분 실행 허용 — drctQty에 도달하면 DONE */
    @Column(name = "cmpl_qty", nullable = false)
    private Long cmplQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private PutawayTaskStatus status;

    /** 지시 완료 시각 (DONE 전이 시점) */
    @Column(name = "cmpl_dt")
    private LocalDateTime cmplDt;

    @Builder
    private PutawayTask(IbLine ibLine, Lot lot, Loc toLoc, Long drctQty) {
        this.ibLine = ibLine;
        this.lot = lot;
        this.toLoc = toLoc;
        this.drctQty = drctQty;
        this.cmplQty = 0L;
        this.status = PutawayTaskStatus.DIRECTED;
    }

    /** 잔여수량 (파생값 — 컬럼 아님) */
    public long remainingQty() {
        return drctQty - cmplQty;
    }

    /** 실행 반영 (부분 허용, 잔여수량 검증은 서비스가 먼저 한다). 전량 도달 시 DONE 전이 */
    public void execute(long qty) {
        this.cmplQty += qty;
        if (cmplQty.equals(drctQty)) {
            this.status = PutawayTaskStatus.DONE;
            this.cmplDt = LocalDateTime.now();
        }
    }

    /**
     * 지시 취소 (행 보존). 실행 실적이 있으면 취소하지 않는다 — 이미 옮긴 실물을 되돌리는 것은
     * 재고이동 화면의 일이다 (docs/design.md 「v1에서 제외하는 것」: 적치완료 취소).
     * 예약 해제는 서비스가 함께 한다.
     */
    public void cancel() {
        if (cmplQty != 0L) {
            throw new IllegalStateException("이미 실행된 수량이 있어 취소할 수 없습니다 (완료 " + cmplQty + ")");
        }
        this.status = PutawayTaskStatus.CANCELLED;
    }
}
