package com.project.wmsback.inventory.entity;

import com.project.common.entity.BaseEntity;
import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.Lot;
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

/**
 * 재고조정 원장 (append-only 자기완결 로그). 수정·삭제하지 않는다. 실적 시각 = createdAt.
 * 장부와 실물이 맞는 상태에서 둘을 함께 증감시키는 의도된 처분(폐기·견본출고)의 기록이다 —
 * 장부와 실물이 어긋났을 때 장부를 실물에 맞추는 재고조사와는 시작 상태부터 다르다.
 * 취소 경로 없음 — 되돌리는 것도 새 조정 1건(사유 ERR_ADJ)이다.
 *
 * 재고 실체는 inv_hist의 ADJUST 1행(부호 있음, rfn_doc_typ=INV_ADJ, rfn_doc_no=adjNo)이고,
 * 번호가 건당 유일이라 실적 ↔ 이력이 1:1로 매칭된다.
 * hldNo가 있으면 보류 라인 — 그 조정이 inv_hld_rlz_acrst 해제 실적(사유 ADJ)을 함께 남겼다는 뜻이다.
 * 보류 건 id가 아니라 번호를 담는 이유는 로트변경 로그의 Lot 번호 스냅샷과 같다 (자기완결).
 */
@Entity
@Table(name = "inv_adj", uniqueConstraints = @UniqueConstraint(name = "uq_inv_adj_no", columnNames = "adj_no"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvAdj extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inv_adj_id")
    private Long id;

    /** 재고조정 번호 (nbr_rule INV_ADJ_NO). inv_hist.rfn_doc_no로 실려 실적 ↔ 이력이 매칭된다 */
    @Column(name = "adj_no", nullable = false, length = 30)
    private String adjNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loc_id", nullable = false)
    private Loc loc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    /** 조정전수량 — 화면 입력값이 아니라 재고 행 락을 걸고 다시 읽은 onHandQty (조사의 cfmSysQty와 같은 성격) */
    @Column(name = "adj_bfr_qty", nullable = false)
    private Long adjBfrQty;

    /** 조정수량 (부호 있음 — 양수 증가 / 음수 감소). 0은 금지 */
    @Column(name = "adj_qty", nullable = false)
    private Long adjQty;

    /** 소진한 보류 건의 번호 (보류 라인). NULL이면 가용 라인 — 라인 종류의 유일한 판별자다 */
    @Column(name = "hld_no", length = 30)
    private String hldNo;

    /** 조정사유 코드 (공통코드 INV_ADJ_RSN). ETC(기타)일 때만 rsnDscr 필수 */
    @Column(name = "rsn_cd", nullable = false, length = 10)
    private String rsnCd;

    @Column(name = "rsn_dscr", length = 200)
    private String rsnDscr;

    @Builder
    private InvAdj(String adjNo, Prod prod, Loc loc, Lot lot,
                   Long adjBfrQty, Long adjQty, String hldNo, String rsnCd, String rsnDscr) {
        this.adjNo = adjNo;
        this.prod = prod;
        this.loc = loc;
        this.lot = lot;
        this.adjBfrQty = adjBfrQty;
        this.adjQty = adjQty;
        this.hldNo = hldNo;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
    }
}
