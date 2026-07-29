package com.project.wmsback.master.entity;

import com.project.wmsback.common.entity.BaseEntity;
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

import java.math.BigDecimal;

/**
 * 상품 포장. <b>(상품, 단위) 한 조합이 한 행</b>이다 — 한 상품이 낱개 · 박스 · 파렛트를
 * 동시에 가질 수 있고, 낱개수량과 중량은 그 조합마다 다르다.
 * <p>
 * 중량을 {@code Prod}에 직접 두지 않은 이유가 이것이다. 상품당 한 칸이면 그 값이 어느
 * 포장 기준인지 이름에 안 드러나고, 박스 중량을 {@code 낱개중량 × 낱개수량}으로 파생하게 되어
 * 포장재 무게(tare)가 사라진다. 파렛트처럼 tare가 큰 단위에서 무시할 수 없는 오차다.
 * <p>
 * PK는 {@code CLAUDE.md} 규칙대로 {@code prod_uom_id} 대리키이고, 실제 grain은
 * {@code uq_prod_uom (prod_id, uom_cd)}이 강제한다.
 */
@Entity
@Table(name = "prod_uom")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProdUom extends BaseEntity {

    @Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prod_uom_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prod_id", nullable = false)
    private Prod prod;

    /** 단위 코드 (공통코드 {@code UOM} 그룹 참조, FK 없음 — 존재 검증은 ProdService) */
    @Column(name = "uom_cd", nullable = false, length = 20)
    private String uomCd;

    /** 이 단위 1개가 낱개 몇 개인가 (예: BOX 1개 = 24). 낱개 그 자체면 1 */
    @Column(name = "ea_qty", nullable = false)
    private Long eaQty;

    /** 이 단위 1개의 중량(kg). 포장재 무게를 포함한 실측값이며 미측정이면 null */
    @Column(name = "wgt", precision = 12, scale = 3)
    private BigDecimal wgt;

    @Builder
    private ProdUom(String uomCd, Long eaQty, BigDecimal wgt) {
        this.uomCd = uomCd;
        this.eaQty = eaQty;
        this.wgt = wgt;
    }

    /**
     * 낱개수량과 중량만 고친다. 상품과 단위 코드는 {@code uq_prod_uom (prod_id, uom_cd)}를
     * 이루는 값이라 바꾸지 않는다 — 바꾸려면 지우고 다시 넣는다.
     */
    public void update(Long eaQty, BigDecimal wgt) {
        this.eaQty = eaQty;
        this.wgt = wgt;
    }

    /** {@link Prod#addUom} 전용 — 양방향 연관의 주인 쪽을 채운다 */
    void assignProd(Prod prod) {
        this.prod = prod;
    }
}
