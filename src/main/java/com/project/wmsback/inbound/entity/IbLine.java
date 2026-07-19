package com.project.wmsback.inbound.entity;

import com.project.wmsback.common.entity.BaseEntity;
import com.project.wmsback.master.entity.Sku;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 입고 라인. 부분입고/검수/적치 진행률을 수량으로만 표현한다.
 * Lot은 라인이 아니라 입고 처리(재고 이력) 단위로 기록되므로 여기엔 두지 않는다.
 */
@Entity
@Table(name = "ib_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IbLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ib_line_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ib_order_id", nullable = false)
    private IbOrder ibOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private Sku sku;

    /** 입고 예정 수량 */
    @Column(name = "expct_qty", nullable = false)
    private Long expctQty;

    /** 검수(개수 확인) 완료된 실제 입고(스테이징 입) 수량 누계 */
    @Column(name = "rcvd_qty", nullable = false)
    private Long rcvdQty;

    /** 적치 완료 수량 누계 (스테이징 → 보관 MOVE 반영분) */
    @Column(name = "ptwy_qty", nullable = false)
    private Long ptwyQty;

    @Builder
    private IbLine(Sku sku, Long expctQty) {
        this.sku = sku;
        this.expctQty = expctQty;
        this.rcvdQty = 0L;
        this.ptwyQty = 0L;
    }

    void assignOrder(IbOrder ibOrder) {
        this.ibOrder = ibOrder;
    }

    /** 검수 반영 (증분 누적). 검수한 수량은 전량 재고로 잡힌다 */
    public void receive(long qty) {
        this.rcvdQty += qty;
    }

    /** 검수 취소 (검수 건 하나를 되돌림) */
    public void cancelReceive(long qty) {
        this.rcvdQty -= qty;
    }

    /** 적치 반영 (증분 누적). 어떤 Lot에서 왔는지는 상관없이 이동한 총량만 더한다 (rcvdQty와 동일한 패턴) */
    public void putaway(long qty) {
        this.ptwyQty += qty;
    }
}
