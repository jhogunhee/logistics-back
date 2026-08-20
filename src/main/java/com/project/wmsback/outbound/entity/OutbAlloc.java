package com.project.wmsback.outbound.entity;

import com.project.common.entity.BaseEntity;
import com.project.wmsback.inventory.entity.Inv;
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
 * 재고 할당 레코드. 어떤 주문라인이 어떤 재고(상품+Loc+Lot)를 몇 개 예약했는지.
 * FEFO(+ 납품기한 필터) 결과가 기록된다. 할당 취소 시 삭제 + Inv.alocQty 복원.
 */
@Entity
@Table(name = "outb_alloc")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutbAlloc extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outb_alloc_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outb_line_id", nullable = false)
    private OutbLine outbLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inv_id", nullable = false)
    private Inv inv;

    /** 할당 수량 (부분할당 허용: 라인 orderQty보다 합계가 작을 수 있음) */
    @Column(name = "aloc_qty", nullable = false)
    private Long alocQty;

    /** 피킹 완료 수량. 피킹 리스트는 할당을 로케이션 순으로 정렬해 생성 */
    @Column(name = "pikng_qty", nullable = false)
    private Long pikngQty;

    /**
     * 이 할당을 만든 할당 전략 (느슨한 참조). NULL = 수동할당 또는 전략 미설정 기간의 기본 동작 —
     * 「전략 없이 만들어짐」이 두 경우를 한 뜻으로 덮는다.
     */
    @Column(name = "aloc_stgy_id")
    private Long alocStgyId;

    /** 할당에 사용된 전략 리비전. stgy_rvsn과 조합해 "그때의 정의"를 재구성한다 (P5) */
    @Column(name = "rvsn_no")
    private Long rvsnNo;

    @Builder
    private OutbAlloc(OutbLine outbLine, Inv inv, Long alocQty, Long alocStgyId, Long rvsnNo) {
        this.outbLine = outbLine;
        this.inv = inv;
        this.alocQty = alocQty;
        this.pikngQty = 0L;
        // 짝으로만 채운다 — ck_outb_alloc_stgy가 한쪽만 있는 상태를 거부한다
        this.alocStgyId = rvsnNo != null ? alocStgyId : null;
        this.rvsnNo = alocStgyId != null ? rvsnNo : null;
    }

    /**
     * 같은 (라인, 재고) 조합에 더 할당할 때 기존 행에 합산한다.
     * DB에 그 조합의 UNIQUE가 없어 새 행을 만들어도 저장은 되지만, 같은 라인이 같은 재고를
     * 가리키는 행이 둘이면 화면과 해제 단위가 이유 없이 쪼개진다.
     *
     * <p><b>전략 컬럼은 처음 값을 유지한다.</b> 나중 실행의 전략으로 덮어쓰면 이미 기록된
     * 수량의 근거가 바뀐다 — 실행 단위의 정확한 이력은 stgy_exec_log가 갖는다.
     */
    public void addQty(long qty) {
        this.alocQty += qty;
    }

    /**
     * 해제 가능 여부. 피킹이 시작된 할당은 실물이 이미 나갔거나 나가는 중이라
     * 되돌리려면 역방향 이동이 필요한데 v1이 지원하지 않는다.
     * 참고 시스템의 「마지막 차수의 미지시분만 취소」가 여기서는 이 조건 하나로 표현되고,
     * 그래서 할당차수 컬럼을 두지 않는다.
     */
    public boolean releasable() {
        return pikngQty == 0L;
    }

    /**
     * 결품 종결 — <b>할당수량을 실제 피킹수량까지 낮춘다.</b> 지시 쪽
     * {@code PikngTask.closeShort()}와 한 트랜잭션에서 짝으로 호출된다.
     *
     * <p>이동지시와 갈리는 유일한 지점이다 — 이동지시는 예약을 지시 자신이 들고 있어
     * {@code drct_qty}만 낮추면 끝나지만, 피킹은 <b>예약의 주인이 이 행</b>이다. 여기를 안 낮추면
     * 「30 줘야 하는데 25만 줬다」가 남아 {@code countUnpickedByOrderId}가 계속 1을 세고
     * 주문이 영영 PICKING에 머문다.
     *
     * <p>{@code pikng_qty = 0}이면 결품 종결이 아니라 지시취소 대상이다 — 낮추면
     * {@code ck_aloc_qty(aloc_qty > 0)}도 깨진다.
     */
    public void closeShort() {
        if (pikngQty == 0L) {
            throw new IllegalStateException("피킹 실적이 없는 할당은 결품 종결할 수 없습니다 (할당 " + alocQty + ")");
        }
        this.alocQty = this.pikngQty;
    }

    /**
     * 피킹 실적 누적 — 이 메서드가 유일한 증가 경로다. {@link #releasable()}과 할당해제 가드가
     * 이 값을 보므로, 다른 경로가 생기면 판정이 갈라진다. 항등식
     * {@code pikng_qty = pikng_task.cmpl_qty = SUM(pikng_acrst.pikng_qty)}의 주문 도메인 쪽 축이고,
     * 실행 서비스가 지시·실적과 한 트랜잭션에서 함께 갱신한다.
     */
    public void addPikngQty(long qty) {
        if (pikngQty + qty > alocQty) {
            throw new IllegalStateException("피킹수량이 할당수량을 초과합니다 (할당 " + alocQty
                    + ", 기피킹 " + pikngQty + ", 요청 " + qty + ")");
        }
        this.pikngQty += qty;
    }
}
