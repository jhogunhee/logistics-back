package com.project.omsback.outbound.entity;

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
 * 출고주문 라인. 주문 수량만 보유한다.
 * 할당/피킹 진행 수량은 확정 시 생성된 WMS 출고주문 쪽(outb_alloc 집계)이 갖는다 — 여기로 되돌려 쓰지 않는다.
 */
@Entity
@Table(name = "oms_outb_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OmsOutbLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oms_outb_line_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oms_outb_order_id", nullable = false)
    private OmsOutbOrder omsOutbOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    /**
     * 주문 수량. 출고단위({@code prod.outb_uom_cd}) 기준이라 확정 시 그대로 복사된다 —
     * 입고주문(발주는 입고단위)과 달리 환산이 없다. 출고는 재고를 그대로 덜어내는 일이라
     * 저장 단위와 주문 단위가 갈릴 이유가 없다.
     */
    @Column(name = "odr_qty", nullable = false)
    private Long odrQty;

    @Builder
    private OmsOutbLine(Prod prod, Long odrQty) {
        this.prod = prod;
        this.odrQty = odrQty;
    }

    void assignOrder(OmsOutbOrder omsOutbOrder) {
        this.omsOutbOrder = omsOutbOrder;
    }
}
