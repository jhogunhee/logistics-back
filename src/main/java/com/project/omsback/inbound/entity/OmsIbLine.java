package com.project.omsback.inbound.entity;

import com.project.wmsback.common.entity.BaseEntity;
import com.project.wmsback.master.entity.Prod;
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
 * 입고주문 라인. 발주 수량만 보유한다.
 * 검수/적치 진행 수량은 확정 시 생성된 ASN 라인(ib_line)이 갖는다 — 여기로 되돌려 쓰지 않는다.
 */
@Entity
@Table(name = "oms_ib_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OmsIbLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oms_ib_line_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oms_ib_order_id", nullable = false)
    private OmsIbOrder omsIbOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    /** 발주 수량. 확정 시 ASN 라인의 expct_qty로 복사된다 */
    @Column(name = "order_qty", nullable = false)
    private Long orderQty;

    @Builder
    private OmsIbLine(Prod prod, Long orderQty) {
        this.prod = prod;
        this.orderQty = orderQty;
    }

    void assignOrder(OmsIbOrder omsIbOrder) {
        this.omsIbOrder = omsIbOrder;
    }
}
