package com.project.mdm.prod.entity;

import com.project.common.entity.BaseEntity;
import com.project.mdm.vendor.entity.Vendor;
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
 * 상품 거래처 마스터 — 상품×벤더의 발주 기준값(자동발주가 읽는다).
 * <p>
 * 상품에 컬럼으로 두지 않은 이유는 이 값들이 상품의 성질이 아니라 <b>거래 조건</b>이기 때문이다 —
 * 같은 상품이라도 벤더가 다르면 리드타임도 최소주문수량도 다르다. 한 상품이 벤더를 여럿 가질 수 있고
 * 대표는 {@code prty}로 고른다(작을수록 우선, 동률이면 id 오름차순).
 * <p>
 * 발주점·발주 상한은 <b>낱개(EA)</b>다 — 창고 수량과 직접 비교하는 값이라 저장 단위를 따라간다.
 * 최소주문수량만 <b>입고단위</b>다 — 발주 수량({@code oms_ib_line.odr_qty})이 입고단위이기 때문.
 */
@Entity
@Table(name = "prod_vndr")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProdVndr extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prod_vndr_id")
    private Long id;

    /** 발주할 상품. FK는 없다 — 존재 검증은 ProdVndrService, 상품 삭제 가드는 ProdVndrRefChecker */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    /** 공급 벤더. 벤더 삭제 가드도 ProdVndrRefChecker가 함께 답한다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    /** 발주점 (EA). 순재고(가용 + 미입고 ASN + 미확정 발주)가 이 아래면 발주 대상 */
    @Column(name = "min_qty", nullable = false)
    private Long minQty;

    /** 발주 상한 (EA). 순재고를 여기까지 채우는 수량을 발주한다 */
    @Column(name = "max_qty", nullable = false)
    private Long maxQty;

    /** 최소주문수량 (입고단위). 부족량이 이보다 적어도 이만큼은 시킨다 */
    @Column(name = "min_odr_qty", nullable = false)
    private Long minOdrQty;

    /** 리드타임(일). 발주일 + 이 일수가 입고 예정일이 된다 */
    @Column(name = "lead_days", nullable = false)
    private Integer leadDays;

    /** 대표 벤더 우선순위. 작을수록 우선 */
    @Column(name = "prty", nullable = false)
    private Integer prty;

    @Builder
    private ProdVndr(Prod prod, Vendor vendor, Long minQty, Long maxQty,
                     Long minOdrQty, Integer leadDays, Integer prty) {
        this.prod = prod;
        this.vendor = vendor;
        this.minQty = minQty;
        this.maxQty = maxQty;
        this.minOdrQty = minOdrQty;
        this.leadDays = leadDays;
        this.prty = prty;
    }

    public void update(Prod prod, Vendor vendor, Long minQty, Long maxQty,
                       Long minOdrQty, Integer leadDays, Integer prty) {
        this.prod = prod;
        this.vendor = vendor;
        this.minQty = minQty;
        this.maxQty = maxQty;
        this.minOdrQty = minOdrQty;
        this.leadDays = leadDays;
        this.prty = prty;
    }
}
