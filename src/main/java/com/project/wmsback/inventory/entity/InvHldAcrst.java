package com.project.wmsback.inventory.entity;

import com.project.common.entity.BaseEntity;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Lot;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보류 실적 (등록의 append-only 로그). 수정·삭제하지 않는다. 실적 시각 = createdAt.
 * 보류 건과 1:1이지만 자기완결로 둔다 — 건이 갱신(해제누계·상태 전이)돼도 등록 시점 기록이 보존된다.
 * hldNo는 inv_hld를 느슨하게 참조한다 (FK 없음 — inv_hist.rfn_doc_no와 같은 성격).
 */
@Entity
@Table(name = "inv_hld_acrst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvHldAcrst extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inv_hld_acrst_id")
    private Long id;

    @Column(name = "hld_no", nullable = false, length = 30)
    private String hldNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loc_id", nullable = false)
    private Loc loc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @Column(name = "hld_qty", nullable = false)
    private Long hldQty;

    @Column(name = "rsn_cd", nullable = false, length = 10)
    private String rsnCd;

    @Column(name = "rsn_dscr", length = 200)
    private String rsnDscr;

    @Builder
    private InvHldAcrst(String hldNo, Prod prod, Loc loc, Lot lot, Long hldQty, String rsnCd, String rsnDscr) {
        this.hldNo = hldNo;
        this.prod = prod;
        this.loc = loc;
        this.lot = lot;
        this.hldQty = hldQty;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
    }
}
