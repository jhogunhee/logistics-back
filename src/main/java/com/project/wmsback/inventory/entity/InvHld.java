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
 * 재고 보류 건. 등록 즉시 발효(지시→확정 2단계 아님) — inv.hld_qty 증가와 한 트랜잭션.
 * 잔량 = hldQty - rlzQty 파생. 전량 해제돼도 행 보존(RELEASED 전이) — putaway_task 선례.
 * 보류/해제는 물리 이동이 아니라 inv_hist에 기록하지 않고, 원장은 실적 2테이블(inv_hld_acrst/inv_hld_rlz_acrst)이다.
 * 항등식: inv.hld_qty = SUM(HELD 건의 잔량) (대사 대상).
 */
@Entity
@Table(name = "inv_hld", uniqueConstraints = @UniqueConstraint(name = "uq_inv_hld_no", columnNames = "hld_no"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvHld extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inv_hld_id")
    private Long id;

    /** 보류 번호 (건당 유일 — 라인 구조 없음). nbr_rule HLD_NO 채번 */
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

    /** 보류 수량. 등록 시점 가용재고(onHand - aloc - hld) 이내 — 예약분은 보류 불가(배타) */
    @Column(name = "hld_qty", nullable = false)
    private Long hldQty;

    /** 해제 완료 수량 누계. 부분 해제 허용 — hldQty에 도달하면 RELEASED */
    @Column(name = "rlz_qty", nullable = false)
    private Long rlzQty;

    /** 보류 사유 코드 (공통코드 HLD_RSN). ETC(기타)일 때만 rsnDscr 필수 */
    @Column(name = "rsn_cd", nullable = false, length = 10)
    private String rsnCd;

    /** 기타 사유 텍스트. rsnCd = ETC일 때만 사용 */
    @Column(name = "rsn_dscr", length = 200)
    private String rsnDscr;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private InvHldStatus status;

    /** 전량 해제 시각 (RELEASED 전이 시점) */
    @Column(name = "rlz_dt")
    private LocalDateTime rlzDt;

    @Builder
    private InvHld(String hldNo, Prod prod, Loc loc, Lot lot, Long hldQty, String rsnCd, String rsnDscr) {
        this.hldNo = hldNo;
        this.prod = prod;
        this.loc = loc;
        this.lot = lot;
        this.hldQty = hldQty;
        this.rlzQty = 0L;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
        this.status = InvHldStatus.HELD;
    }

    /** 미해제 잔량 (파생값 — 컬럼 아님) */
    public long remainingQty() {
        return hldQty - rlzQty;
    }

    /** 해제 반영 (부분 허용, 잔량 검증은 서비스가 먼저 한다). 전량 도달 시 RELEASED 전이 */
    public void release(long qty) {
        this.rlzQty += qty;
        if (rlzQty.equals(hldQty)) {
            this.status = InvHldStatus.RELEASED;
            this.rlzDt = LocalDateTime.now();
        }
    }
}
