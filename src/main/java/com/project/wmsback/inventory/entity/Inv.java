package com.project.wmsback.inventory.entity;

import com.project.wmsback.common.entity.BaseEntity;
import com.project.wmsback.master.entity.Loc;
import com.project.wmsback.master.entity.Lot;
import com.project.wmsback.master.entity.Prod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 현재고 스냅샷. 키: 상품+Loc+Lot. 가용재고 = onHand - alloc (파생값, 컬럼 아님).
 * 할당 시 락을 거는 지점 (비관적/낙관적 락 비교 대상).
 */
@Entity
@Table(name = "inv", uniqueConstraints = @UniqueConstraint(name = "uq_inv", columnNames = {"prod_id", "loc_id", "lot_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inv extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inv_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loc_id", nullable = false)
    private Loc loc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    /** 실물 보유 수량. 물리 변동(RECEIVE/MOVE/ADJUST/PICK 등) 시에만 증감 */
    @Column(name = "on_hand_qty", nullable = false)
    private Long onHandQty;

    /**
     * 예약 수량 — 출고 할당 전용이 아니라 예약수량 일반. 출고 할당(outb_alloc)과 이동지시(inv_mov_task)가
     * 같은 컬럼으로 선점하고, 실행(피킹/이동확정)이 onHand와 함께 소진한다. 물리 이동이 아니므로 이력에 기록하지 않음.
     * 항등식: alocQty = 원천별 미소진 잔량 합 (대사 대상).
     */
    @Column(name = "aloc_qty", nullable = false)
    private Long alocQty;

    /** 낙관적 락 버전. 비관적 락과의 비교 실험 대상 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder
    private Inv(Prod prod, Loc loc, Lot lot) {
        this.prod = prod;
        this.loc = loc;
        this.lot = lot;
        this.onHandQty = 0L;
        this.alocQty = 0L;
    }

    /** 가용재고 (파생값) */
    public long availableQty() {
        return onHandQty - alocQty;
    }

    /** 물리 증가 (입고/이동 입). 반드시 InvHist 기록과 한 트랜잭션에서 호출한다 */
    public void increaseOnHand(long qty) {
        this.onHandQty += qty;
    }

    /** 물리 감소 (이동 출/검수 취소). 반드시 InvHist 기록과 한 트랜잭션에서 호출한다 */
    public void decreaseOnHand(long qty) {
        this.onHandQty -= qty;
    }

    /** 예약 (이동지시 등록/출고 할당). 가용재고 검증 후 호출한다 — ck_inv_qty(aloc<=onHand)가 최후 방어 */
    public void reserve(long qty) {
        this.alocQty += qty;
    }

    /** 예약 해제/소진 (지시 취소·이동확정, 할당 해제·피킹) */
    public void release(long qty) {
        this.alocQty -= qty;
    }
}
