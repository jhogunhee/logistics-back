package com.project.wmsback.inbound.entity;

import com.project.common.entity.BaseEntity;
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
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    /** 입고 예정 수량 */
    @Column(name = "expct_qty", nullable = false)
    private Long expctQty;

    /** 검수(개수 확인) 완료된 실제 입고(스테이징 입) 수량 누계 */
    @Column(name = "rcvd_qty", nullable = false)
    private Long rcvdQty;

    /** 적치 완료 수량 누계 (스테이징 → 보관 MOVE 반영분) */
    @Column(name = "ptawy_qty", nullable = false)
    private Long ptawyQty;

    /** 검수 불량 수량 누계 — 반품존에 받아 보류된 분. 반품입고만 0보다 크다. 적치 대상이 아니다 */
    @Column(name = "rjct_qty", nullable = false)
    private Long rjctQty;

    @Builder
    private IbLine(Prod prod, Long expctQty) {
        this.prod = prod;
        this.expctQty = expctQty;
        this.rcvdQty = 0L;
        this.ptawyQty = 0L;
        this.rjctQty = 0L;
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

    /** 불량 반영 (증분 누적). 반품존에 받아 보류된다 — rcvdQty와 별개 축이라 적치·확정 조건에 끼지 않는다 */
    public void reject(long qty) {
        this.rjctQty += qty;
    }

    public void cancelReject(long qty) {
        this.rjctQty -= qty;
    }

    /** 적치 반영 (증분 누적). 어떤 Lot에서 왔는지는 상관없이 이동한 총량만 더한다 (rcvdQty와 동일한 패턴) */
    public void putaway(long qty) {
        this.ptawyQty += qty;
    }

    /**
     * 이 라인이 어디까지 왔는지 — 수량 셋에서 그때그때 계산한다. <b>저장하는 값이 아니다.</b>
     * <p>
     * 라인에 상태 컬럼을 두지 않는 이유는 수량과 상태가 두 벌이 되기 때문이다. 검수를 취소했는데
     * 상태를 안 바꾸면 바로 어긋나는데, 수량은 어차피 갱신되므로 거기서 따라 만들면 어긋날 자리가 없다.
     * ({@code docs/design.md} 「상태와 수량의 분담」 — 부분입고를 상태값으로 만들지 않는다)
     * <p>
     * <b>판정의 주인은 여기다</b> — 헤더는 라인 단계를 모아 만든다(검수까지 max, 그 위는 min).
     * 헤더 쪽은 SQL이라 이 메서드를 부를 수 없어 {@code IbOrderRepositoryImpl#lineStage()}가
     * 같은 사다리를 CASE로 옮겨 놓았다. 한쪽을 고치면 반드시 다른 쪽도 고칠 것.
     * <p>
     * "덜 왔으면 검수"를 두지 않는다 — 부분 도착 여부는 진행단계가 아니라 수량 칸(예정·검수·미적치)이
     * 말한다. 그걸 단계에 섞으면 「40개 받아 40개 다 옮겼다」가 영영 검수에 머물러, 확정을 눌러도 되는
     * 상태를 화면이 감춘다.
     *
     * @param hasOpenPtawyDrct 이 라인에 미완료(DIRECTED) 적치지시가 있는가. 라인 스스로는 알 수 없어
     *                         호출부가 넣어준다 — 모른 채 판정하면 조용히 틀리므로 무인자 버전은 두지 않는다
     */
    public IbPrgr progressStatus(boolean hasOpenPtawyDrct) {
        if (ibOrder.getStatus() == IbStatus.CONFIRMED) return IbPrgr.CONFIRMED; // 닫힌 입고 — 결품 포함 확정
        if (rcvdQty + rjctQty == 0) return IbPrgr.SCHEDULED;   // 아직 안 옴 (불량만 와도 온 것이다)
        // 온 것을 다 옮겼다 = 확정 전제조건 충족. 불량만 온 라인도 여기 걸린다 (적치할 양품이 0)
        if (ptawyQty.equals(rcvdQty)) return IbPrgr.PTAWY_CMPL;
        if (hasOpenPtawyDrct || ptawyQty > 0) return IbPrgr.PTAWY_DRCT;
        return IbPrgr.RECEIVING;
    }
}
