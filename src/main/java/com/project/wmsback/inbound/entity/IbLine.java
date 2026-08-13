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

    @Builder
    private IbLine(Prod prod, Long expctQty) {
        this.prod = prod;
        this.expctQty = expctQty;
        this.rcvdQty = 0L;
        this.ptawyQty = 0L;
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
     * 헤더와 같은 {@link IbStatus}를 돌려준다. 값의 뜻이 라인 범위로 좁아질 뿐 어휘가 같아서
     * 화면이 헤더와 같은 뱃지를 그대로 쓰고, 헤더 상태가 왜 그 값인지 라인에서 바로 읽힌다.
     * <p>
     * 검수 축을 먼저 본다 — 적치는 부분검수분에도 할 수 있어 검수와 나란히 굴러가므로, 둘을 한 값에
     * 합치면 뜻이 뭉개진다. 그래서 "아직 더 올 것이 있다"(검수 &lt; 예정)가 적치 진행보다 앞선다.
     */
    public IbStatus progressStatus() {
        if (rcvdQty == 0) return IbStatus.SCHEDULED;      // 아직 안 옴
        if (rcvdQty < expctQty) return IbStatus.RECEIVING; // 덜 옴 (온 것을 다 적치했어도 여기다)
        if (ptawyQty >= rcvdQty) return IbStatus.COMPLETED; // 다 오고 다 옮김
        return IbStatus.RECEIVED;                           // 다 왔고 적치가 남음
    }
}
