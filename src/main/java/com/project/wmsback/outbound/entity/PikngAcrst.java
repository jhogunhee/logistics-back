package com.project.wmsback.outbound.entity;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 피킹 실적 — 실행 1회 = 1행의 append-only 로그. 수정·삭제하지 않는다 (부분 피킹이 N번이면 N행).
 *
 * <p>「실적 = inv_hist」 원칙의 두 번째 예외다 (첫째는 보류 — {@code inv_hld_acrst}).
 * PICK 이력의 참조({@code rfn_doc_no} = 출고번호)는 주문 단위라 실행 건을 <b>지시 단위</b>로
 * 귀속시키지 못한다 — 한 주문에 같은 재고 키를 집품하는 지시가 둘이면 이력만으로 가릴 수 없다.
 * 피킹 실적 취소(백로그)가 도입되면 취소 단위가 이 행이다.
 *
 * <p>prod · fromLoc · lot은 지시와 같은 재고 키 스냅샷이고, 실행 시각·작업자는 감사 컬럼이 담당한다.
 */
@Entity
@Table(name = "pikng_acrst")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PikngAcrst extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pikng_acrst_id")
    private Long id;

    /** 실행한 피킹지시 (FK 없음 — 느슨한 참조) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pikng_task_id", nullable = false)
    private PikngTask pikngTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_loc_id", nullable = false)
    private Loc fromLoc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    /** 이번 실행의 피킹 수량. SUM = pikng_task.cmpl_qty (대사 축) */
    @Column(name = "pikng_qty", nullable = false)
    private Long pikngQty;

    @Builder
    private PikngAcrst(PikngTask pikngTask, Prod prod, Loc fromLoc, Lot lot, Long pikngQty) {
        this.pikngTask = pikngTask;
        this.prod = prod;
        this.fromLoc = fromLoc;
        this.lot = lot;
        this.pikngQty = pikngQty;
    }
}
