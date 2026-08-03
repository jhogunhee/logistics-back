package com.project.wmsback.outbound.entity;

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
 * 출고 주문 라인. 할당/피킹 수량은 컬럼으로 두지 않고 OutbAlloc 집계로 파생
 * (수량-상태 불일치 원천 차단).
 */
@Entity
@Table(name = "outb_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutbLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outb_line_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outb_order_id", nullable = false)
    private OutbOrder outbOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    /** 주문 수량 */
    @Column(name = "odr_qty", nullable = false)
    private Long odrQty;

    @Builder
    private OutbLine(Prod prod, Long odrQty) {
        this.prod = prod;
        this.odrQty = odrQty;
    }

    void assignOrder(OutbOrder outbOrder) {
        this.outbOrder = outbOrder;
    }
}
