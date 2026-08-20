package com.project.wmsback.warehouse.entity;

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
 * 고정 로케이션 마스터. 상품×로케이션 지정 — 피킹존 운영의 근거.
 * 한 로케이션은 한 상품 전용(uq_fxng_loc), 한 상품은 여러 로케이션을 가질 수 있다.
 */
@Entity
@Table(name = "fxng_loc")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FxngLoc extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fxng_loc_id")
    private Long id;

    /** 고정할 상품. FK는 없다 — 존재 검증은 FxngLocService, 상품 삭제 가드는 WmsProdRefChecker */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    /** 고정 로케이션 (STORAGE 전용). FK는 없다 — 로케이션 삭제 가드는 LocRefQueryRepository */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loc_id", nullable = false)
    private Loc loc;

    /** 재보충점. 이 로케이션의 재고가 이 아래로 내려가면 보충 대상 (보충 프로세스 구현 시 사용) */
    @Column(name = "min_qty", nullable = false)
    private Long minQty;

    /** 보충 목표 상한. loc.max_qty 이하 — 등록(FxngLocService)과 loc.max_qty 하향(LocService)이 함께 지킨다 */
    @Column(name = "max_qty", nullable = false)
    private Long maxQty;

    @Builder
    private FxngLoc(Prod prod, Loc loc, Long minQty, Long maxQty) {
        this.prod = prod;
        this.loc = loc;
        this.minQty = minQty;
        this.maxQty = maxQty;
    }

    public void update(Prod prod, Loc loc, Long minQty, Long maxQty) {
        this.prod = prod;
        this.loc = loc;
        this.minQty = minQty;
        this.maxQty = maxQty;
    }
}
