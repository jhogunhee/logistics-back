package com.project.wmsback.inventory.entity;

import com.project.wmsback.master.entity.Loc;
import com.project.wmsback.master.entity.Lot;
import com.project.wmsback.master.entity.Sku;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 현재고 스냅샷. 키: SKU+Loc+Lot. 가용재고 = onHand - alloc (파생값, 컬럼 아님).
 * 할당 시 락을 거는 지점 (비관적/낙관적 락 비교 대상).
 */
@Entity
@Table(name = "inv", uniqueConstraints = @UniqueConstraint(name = "uq_inv", columnNames = {"sku_id", "loc_id", "lot_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Inv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inv_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private Sku sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loc_id", nullable = false)
    private Loc loc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    /** 실물 보유 수량. 물리 변동(RECEIVE/MOVE/ADJUST/PICK 등) 시에만 증감 */
    @Column(name = "on_hand_qty", nullable = false)
    private Long onHandQty;

    /** 할당(예약) 수량. 물리 이동이 아니므로 이력에 기록하지 않음 */
    @Column(name = "alloc_qty", nullable = false)
    private Long allocQty;

    /** 낙관적 락 버전. 비관적 락과의 비교 실험 대상 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private Inv(Sku sku, Loc loc, Lot lot) {
        this.sku = sku;
        this.loc = loc;
        this.lot = lot;
        this.onHandQty = 0L;
        this.allocQty = 0L;
    }

    /** 가용재고 (파생값) */
    public long availableQty() {
        return onHandQty - allocQty;
    }
}
