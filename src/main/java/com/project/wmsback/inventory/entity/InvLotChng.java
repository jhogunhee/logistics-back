package com.project.wmsback.inventory.entity;

import com.project.common.entity.BaseEntity;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.mdm.prod.entity.Prod;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 재고 로트변경 원장 (append-only 자기완결 로그). 수정·삭제하지 않는다. 실적 시각 = createdAt.
 * 「이 로케이션의 이 재고 중 N개는 제조일자가 X였다」 — 원 Lot에서 N개를 빼
 * (상품+입고일자+X) 배치의 Lot으로 넣는다(있으면 병합, 없으면 채번=분할).
 * 취소 경로 없음 — 되돌리는 것도 새 로트변경 1건이다.
 *
 * 재고 실체는 inv_hist의 ADJUST 2행(rfn_doc_typ=LOT_CHNG, rfn_doc_no=lotChngNo)이고,
 * 이 로그의 두 Lot은 번호·날짜를 스냅샷으로 담아 자기완결이다(보류 실적 선례) —
 * from/to Lot id는 FK 없는 느슨한 참조라 엔티티가 아니라 스칼라로 둔다.
 */
@Entity
@Table(name = "inv_lot_chng", uniqueConstraints = @UniqueConstraint(name = "uq_inv_lot_chng_no", columnNames = "lot_chng_no"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvLotChng extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inv_lot_chng_id")
    private Long id;

    /** 로트변경 번호 (nbr_rule LOT_CHNG_NO). inv_hist.rfn_doc_no로 실려 실적 ↔ 이력이 매칭된다 */
    @Column(name = "lot_chng_no", nullable = false, length = 30)
    private String lotChngNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    /** 대상 보관 로케이션 — 로케이션은 바뀌지 않는다 (같은 로케이션 안에서 Lot만 바뀌는 장부 이동) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loc_id", nullable = false)
    private Loc loc;

    @Column(name = "from_lot_id", nullable = false)
    private Long fromLotId;

    @Column(name = "from_lot_no", nullable = false, length = 30)
    private String fromLotNo;

    @Column(name = "from_mfg_dt", nullable = false)
    private LocalDate fromMfgDt;

    @Column(name = "from_expiry_dt", nullable = false)
    private LocalDate fromExpiryDt;

    @Column(name = "to_lot_id", nullable = false)
    private Long toLotId;

    @Column(name = "to_lot_no", nullable = false, length = 30)
    private String toLotNo;

    @Column(name = "to_mfg_dt", nullable = false)
    private LocalDate toMfgDt;

    @Column(name = "to_expiry_dt", nullable = false)
    private LocalDate toExpiryDt;

    @Column(name = "chng_qty", nullable = false)
    private Long chngQty;

    /** 목적지 Lot을 이번에 채번했는가 — true 분할(새 Lot) / false 병합(기존 Lot 합류) */
    @Column(name = "to_lot_new_yn", nullable = false)
    private Boolean toLotNewYn;

    /** 변경 사유 코드 (공통코드 LOT_ATTR_RSN 재사용). ETC(기타)일 때만 rsnDscr 필수 */
    @Column(name = "rsn_cd", nullable = false, length = 10)
    private String rsnCd;

    @Column(name = "rsn_dscr", length = 200)
    private String rsnDscr;

    @Builder
    private InvLotChng(String lotChngNo, Prod prod, Loc loc,
                       Long fromLotId, String fromLotNo, LocalDate fromMfgDt, LocalDate fromExpiryDt,
                       Long toLotId, String toLotNo, LocalDate toMfgDt, LocalDate toExpiryDt,
                       Long chngQty, Boolean toLotNewYn, String rsnCd, String rsnDscr) {
        this.lotChngNo = lotChngNo;
        this.prod = prod;
        this.loc = loc;
        this.fromLotId = fromLotId;
        this.fromLotNo = fromLotNo;
        this.fromMfgDt = fromMfgDt;
        this.fromExpiryDt = fromExpiryDt;
        this.toLotId = toLotId;
        this.toLotNo = toLotNo;
        this.toMfgDt = toMfgDt;
        this.toExpiryDt = toExpiryDt;
        this.chngQty = chngQty;
        this.toLotNewYn = toLotNewYn;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
    }
}
