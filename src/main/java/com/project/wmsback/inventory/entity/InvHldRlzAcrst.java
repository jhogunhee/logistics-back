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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보류 해제 실적 (append-only 로그). 해제는 특정 보류 건(hldNo)을 지목하며 부분 해제 허용 — N번 해제면 N행.
 * 해제 사유는 등록 사유와 별개의 코드 그룹(HLD_RLZ_RSN)이다 — 「왜 묶었나」와 「왜 풀었나」는 다른 질문이다.
 */
@Entity
@Table(name = "inv_hld_rlz_acrst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvHldRlzAcrst extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inv_hld_rlz_acrst_id")
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

    @Column(name = "rlz_qty", nullable = false)
    private Long rlzQty;

    @Column(name = "rsn_cd", nullable = false, length = 10)
    private String rsnCd;

    @Column(name = "rsn_dscr", length = 200)
    private String rsnDscr;

    @Builder
    private InvHldRlzAcrst(String hldNo, Prod prod, Loc loc, Lot lot, Long rlzQty, String rsnCd, String rsnDscr) {
        this.hldNo = hldNo;
        this.prod = prod;
        this.loc = loc;
        this.lot = lot;
        this.rlzQty = rlzQty;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
    }
}
