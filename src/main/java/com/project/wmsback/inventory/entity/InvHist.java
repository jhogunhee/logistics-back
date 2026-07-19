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
 * 재고 이력 (append-only 원장). 모든 물리 변동을 ±수량으로 기록.
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

    /** 유형 */
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

    /**
     * 입고 라인 ID. RECEIVE(및 그 취소 ADJUST) 건에만 채워짐.
     * ref_doc_no(주문 단위)로는 라인을 못 좁히므로 별도로 둔다 (FK 없는 느슨한 참조 — ref_doc_no와 동일 패턴).
     */
    @Column(name = "ib_line_id")
    private Long ibLineId;

    /**
     * MOVE의 출발지/도착지 로케이션 ID. 화면에서 "출발지 → 도착지"를 보여주기 위한 것으로, 두 다리(출발 -건 / 도착 +건)
     * 모두 똑같은 값을 갖는다 — 그래서 어느 한 행만 봐도 상대를 찾는 조인 없이 바로 전체 그림을 알 수 있다.
     * 재고 키(SKU+Loc+Lot)와 이력 합계=스냅샷 불변식은 그대로 loc/qty가 담당하고, 이 둘은 표시 전용 부가 정보다.
     * FK 없는 느슨한 참조 (ib_line_id/ref_doc_no와 동일 패턴). MOVE가 아닌 나머지 타입은 항상 null.
     */
    @Column(name = "from_loc_id")
    private Long fromLocId;

    @Column(name = "to_loc_id")
    private Long toLocId;

    /**
     * 검수 취소(ADJUST) 건이 되돌리는 원본 RECEIVE 건의 inv_hist_id. ADJUST가 아니면 항상 null.
     * 이게 없으면 화면이 "이미 취소된 RECEIVE"를 구분 못 해서 취소된 건도 다시 취소 가능한 것처럼 보여준다.
     * FK 없는 느슨한 참조 (ib_line_id/ref_doc_no와 동일 패턴).
     */
    @Column(name = "cancels_inv_hist_id")
    private Long cancelsInvHistId;

    @Builder
    private InvHist(TxType txType, Sku sku, Loc loc, Lot lot, Long qty, RefDocType refDocType, String refDocNo,
                    Long ibLineId, Long fromLocId, Long toLocId, Long cancelsInvHistId) {
        this.txType = txType;
        this.sku = sku;
        this.loc = loc;
        this.lot = lot;
        this.qty = qty;
        this.refDocType = refDocType;
        this.refDocNo = refDocNo;
        this.ibLineId = ibLineId;
        this.fromLocId = fromLocId;
        this.toLocId = toLocId;
        this.cancelsInvHistId = cancelsInvHistId;
    }
}
