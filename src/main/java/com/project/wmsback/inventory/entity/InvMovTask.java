package com.project.wmsback.inventory.entity;

import com.project.common.entity.BaseEntity;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.mdm.prod.entity.Prod;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 이동지시 (보관 ↔ 보관 2단계 이동: 지시=예약 → 확정=실물 MOVE).
 * 등록 시 FROM 재고의 aloc_qty를 선점하고, 확정이 inv_hist MOVE 2행을 남기며 예약을 소진한다.
 * 실적 테이블은 따로 없다 — 분할확정 실적은 inv_hist에 확정 횟수만큼 쌓인다 (rfn_doc_no = inv_mov_no).
 * 지시는 권고가 아니라 명령: 지시 TO와 다른 로케이션으로 확정할 수 없고, 다른 곳에 두려면 잔량 취소 후 재지시한다.
 */
@Entity
@Table(name = "inv_mov_task", uniqueConstraints = @UniqueConstraint(name = "uq_inv_mov_no", columnNames = "inv_mov_no"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvMovTask extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inv_mov_task_id")
    private Long id;

    /** 이동지시 번호 (건당 유일 — 라인 구조 없음). inv_hist 실적이 rfn_doc_no만으로 지시와 매칭되는 전제 */
    @Column(name = "inv_mov_no", nullable = false, length = 30)
    private String invMovNo;

    /** 이동구분. 재고이동 화면의 확정·취소는 INV_MOV만 허용된다 (적치·피킹 유형은 각자의 경로 전용) */
    @Enumerated(EnumType.STRING)
    @Column(name = "mov_dvsn", nullable = false, length = 10)
    private InvMovDvsn movDvsn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    /** 출발 보관 로케이션. 등록 시 이 로케이션 재고의 aloc_qty를 잔여수량만큼 선점한다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_loc_id", nullable = false)
    private Loc fromLoc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_loc_id", nullable = false)
    private Loc toLoc;

    /** 지시 수량. 부분확정 후 잔량 취소 시 완료수량으로 차감된다 */
    @Column(name = "drct_qty", nullable = false)
    private Long drctQty;

    /** 확정(실물 MOVE) 완료 수량 누계. 부분확정 허용 — drctQty에 도달하면 DONE */
    @Column(name = "cmpl_qty", nullable = false)
    private Long cmplQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private InvMovStatus status;

    /** 지시 완료 시각 (DONE 전이 시점) */
    @Column(name = "cmpl_dt")
    private LocalDateTime cmplDt;

    /**
     * 짝 피킹지시 (RPLN만, 느슨한 참조). 보충은 피킹지시에 매달린 지시라 주인은 피킹지시 쪽이고,
     * 피킹 실행이 「짝 보충이 끝났나」를 이 값으로 찾는다. 살아 있는 보충은 피킹지시당 하나(부분 유니크)
     */
    @Column(name = "pikng_task_id")
    private Long pikngTaskId;

    @Builder
    private InvMovTask(String invMovNo, InvMovDvsn movDvsn, Prod prod, Lot lot, Loc fromLoc, Loc toLoc, Long drctQty,
                       Long pikngTaskId) {
        this.pikngTaskId = pikngTaskId;
        this.invMovNo = invMovNo;
        this.movDvsn = movDvsn;
        this.prod = prod;
        this.lot = lot;
        this.fromLoc = fromLoc;
        this.toLoc = toLoc;
        this.drctQty = drctQty;
        this.cmplQty = 0L;
        this.status = InvMovStatus.DIRECTED;
    }

    /** 잔여수량 (파생값 — 컬럼 아님) */
    public long remainingQty() {
        return drctQty - cmplQty;
    }

    /** 확정 반영 (부분 허용, 잔여수량 검증은 서비스가 먼저 한다). 전량 도달 시 DONE 전이 */
    public void confirm(long qty) {
        this.cmplQty += qty;
        if (cmplQty.equals(drctQty)) {
            done();
        }
    }

    /**
     * 잔량 취소. 확정 실적이 없으면 CANCELLED(행 보존 — putaway_task와 같은 방식),
     * 부분확정 후면 지시수량을 완료수량으로 차감하고 DONE 전이. 예약 해제는 서비스가 함께 한다.
     */
    public void cancelRemainder() {
        if (cmplQty == 0L) {
            this.status = InvMovStatus.CANCELLED;
        } else {
            this.drctQty = this.cmplQty;
            done();
        }
    }

    private void done() {
        this.status = InvMovStatus.DONE;
        this.cmplDt = LocalDateTime.now();
    }
}
