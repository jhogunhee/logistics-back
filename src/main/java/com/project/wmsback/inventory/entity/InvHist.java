package com.project.wmsback.inventory.entity;

import com.project.wmsback.common.entity.BaseEntity;
import com.project.wmsback.master.entity.Loc;
import com.project.wmsback.master.entity.Lot;
import com.project.wmsback.master.entity.Sku;
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

/**
 * 재고 수불 이력 (append-only 원장). 모든 물리 변동을 ±수량으로 기록.
 * 스냅샷(Inv)과 한 트랜잭션에서 갱신한다 (불변식: 이력 합계 = 스냅샷).
 * MOVE는 출발지(-)/도착지(+) 2건으로 기록.
 */
@Entity
@Table(name = "inv_hist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvHist extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inv_hist_id")
    private Long id;

    /** 수불 유형 */
    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false, length = 10)
    private TxType txType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private Sku sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loc_id", nullable = false)
    private Loc loc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    /** 변동 수량 (증가 +, 감소 -). 0 금지 */
    @Column(name = "qty", nullable = false)
    private Long qty;

    /** 참조 문서 유형 (수동 조정 시 null) */
    @Enumerated(EnumType.STRING)
    @Column(name = "ref_doc_type", length = 10)
    private RefDocType refDocType;

    /** 참조 문서 번호 (입고번호/출고번호). 이력 → 원인 문서 추적용 */
    @Column(name = "ref_doc_no", length = 30)
    private String refDocNo;

    @Builder
    private InvHist(TxType txType, Sku sku, Loc loc, Lot lot, Long qty, RefDocType refDocType, String refDocNo) {
        this.txType = txType;
        this.sku = sku;
        this.loc = loc;
        this.lot = lot;
        this.qty = qty;
        this.refDocType = refDocType;
        this.refDocNo = refDocNo;
    }
}
