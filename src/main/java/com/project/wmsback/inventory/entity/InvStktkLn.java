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
 * 재고조사 라인. 재고 키(상품+Loc+Lot) 단위로 전산수량과 실사수량을 나란히 든다.
 *
 * 조정수량 = 실사수량(stktkQty) − 확정시점 전산수량(cfmSysQty) 파생 — 컬럼으로 두지 않는다.
 * 조사 생성 시점 스냅샷(sysQty)은 「조사 시작 때는 얼마였나」의 기록이고, 조정의 기준이 아니다.
 */
@Entity
@Table(name = "inv_stktk_ln", uniqueConstraints = @UniqueConstraint(
        name = "uq_inv_stktk_ln", columnNames = {"inv_stktk_id", "prod_id", "loc_id", "lot_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvStktkLn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inv_stktk_ln_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inv_stktk_id", nullable = false)
    private InvStktk invStktk;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loc_id", nullable = false)
    private Loc loc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    /** 조사 생성 시점의 전산수량 스냅샷 (onHandQty). 표시·감사용 — 확정 기준은 cfmSysQty다 */
    @Column(name = "sys_qty", nullable = false)
    private Long sysQty;

    /** 실사수량. null = 미조사(확정 시 건너뜀), 0 = 실물 없음(전량 차감). 출고단위 기준 */
    @Column(name = "stktk_qty")
    private Long stktkQty;

    /**
     * 확정 시점에 재고 행 락을 걸고 다시 읽은 전산수량(= 조정전수량). 확정 전에는 null.
     * 조정수량을 이 값 기준으로 잡기 때문에 확정 후 onHandQty가 실사수량과 정확히 일치한다.
     */
    @Column(name = "cfm_sys_qty")
    private Long cfmSysQty;

    /** 조정사유 코드 (공통코드 ADJ_RSN). 차이가 0이 아닌 라인만 필수. ETC일 때만 rsnDscr 필수 */
    @Column(name = "rsn_cd", length = 10)
    private String rsnCd;

    /** 기타 사유 텍스트. rsnCd = ETC일 때만 사용 */
    @Column(name = "rsn_dscr", length = 200)
    private String rsnDscr;

    @Builder
    private InvStktkLn(Prod prod, Loc loc, Lot lot, Long sysQty) {
        this.prod = prod;
        this.loc = loc;
        this.lot = lot;
        this.sysQty = sysQty;
    }

    void assignStktk(InvStktk invStktk) {
        this.invStktk = invStktk;
    }

    /** 실사수량·사유 입력 (작성 중에만 — 상태 검증은 헤더가 한다). qty가 null이면 미조사로 되돌린다 */
    public void count(Long stktkQty, String rsnCd, String rsnDscr) {
        this.stktkQty = stktkQty;
        this.rsnCd = rsnCd;
        this.rsnDscr = rsnDscr;
    }

    /** 전산수량 재스냅샷 — 조사 중 다른 업무로 재고가 변했을 때 화면 기준값을 맞춘다 (실사수량은 건드리지 않는다) */
    public void resync(long sysQty) {
        this.sysQty = sysQty;
    }

    /** 확정 시점 전산수량 기록. 이 값과 실사수량의 차이가 곧 조정수량이다 */
    public void confirm(long cfmSysQty) {
        this.cfmSysQty = cfmSysQty;
    }

    /** 실사수량이 입력된 라인만 확정 대상이다 (null = 미조사) */
    public boolean counted() {
        return stktkQty != null;
    }

    /** 확정 기준 조정수량 (실사 − 확정시점 전산). 확정 전에는 의미가 없어 0을 돌려준다 */
    public long adjQty() {
        if (stktkQty == null || cfmSysQty == null) {
            return 0L;
        }
        return stktkQty - cfmSysQty;
    }
}
