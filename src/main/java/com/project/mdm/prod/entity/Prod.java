package com.project.mdm.prod.entity;

import com.project.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 상품 마스터. 보관/출고 규칙(온도대, 납품 허용 잔여수명)을 상품 단위로 정의.
 */
@Entity
@Table(name = "prod")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prod extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prod_id")
    private Long id;

    /** 상품 코드 (업무 식별자, 예: PROD-0001) */
    @Column(name = "prod_cd", nullable = false, length = 30, unique = true)
    private String prodCd;

    /** 상품명 */
    @Column(name = "prod_nm", nullable = false, length = 100)
    private String prodNm;

    /** 보관 온도대. 적치·이동 시 로케이션 온도대와 일치 검증 */
    @Enumerated(EnumType.STRING)
    @Column(name = "tmp_zon", nullable = false, length = 10)
    private TmpZon tmpZon;

    /**
     * 입고단위 코드 (공통코드 {@code UOM} 그룹 참조, FK 없음 — 존재 검증은 ProdService).
     * 벤더에게 발주하고 납품받는 단위다 (예: BOX). 이 단위를 쓰는 곳은 {@code oms_ib_line.odr_qty} 하나뿐이다.
     */
    @Column(name = "inb_uom_cd", nullable = false, length = 20)
    private String inbUomCd;

    /**
     * 출고단위 코드 (공통코드 {@code UOM} 그룹 참조, FK 없음).
     * <b>재고 저장 단위이기도 하다</b> — {@code inv} · {@code inv_hist} · {@code ib_line} ·
     * {@code outb_line} 등 DB의 모든 수량 컬럼이 이 단위 기준이다 ({@code oms_ib_line.odr_qty}만 예외).
     */
    @Column(name = "outb_uom_cd", nullable = false, length = 20)
    private String outbUomCd;

    /** 제조일 기준 총 유통기한(일). NULL = 유통기한 미관리(공산품 등). 시더가 Lot 유통기한 생성 시 사용 */
    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    /**
     * 이 상품이 갖는 포장들. {@code inbUomCd} · {@code outbUomCd}는 반드시 이 안의 한 행을 가리킨다
     * (검증은 ProdService). 환산수량을 상품에 컬럼으로 두지 않고 여기서 파생시킨다.
     */
    @OneToMany(mappedBy = "prod", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProdUom> uoms = new ArrayList<>();

    @Builder
    private Prod(String prodCd, String prodNm, TmpZon tmpZon,
                 String inbUomCd, String outbUomCd, Integer shelfLifeDays) {
        this.prodCd = prodCd;
        this.prodNm = prodNm;
        this.tmpZon = tmpZon;
        this.inbUomCd = inbUomCd;
        this.outbUomCd = outbUomCd;
        this.shelfLifeDays = shelfLifeDays;
    }

    public void update(String prodNm, TmpZon tmpZon,
                       String inbUomCd, String outbUomCd, Integer shelfLifeDays) {
        this.prodNm = prodNm;
        this.tmpZon = tmpZon;
        this.inbUomCd = inbUomCd;
        this.outbUomCd = outbUomCd;
        this.shelfLifeDays = shelfLifeDays;
    }

    public void addUom(ProdUom uom) {
        uoms.add(uom);
        uom.assignProd(this);
    }

    /** 단위 관리 화면의 삭제 — orphanRemoval이 DELETE를 낸다. 컬렉션에 남겨둔 채 리포지토리로
     *  지우면 cascade가 flush 시점에 되살릴 수 있어 반드시 이쪽으로 지운다 */
    public void removeUom(ProdUom uom) {
        uoms.remove(uom);
    }

    /**
     * 포장의 역할(입고단위/출고단위)을 이 단위로 옮긴다 — 단위 관리 화면이 쓴다.
     * 상품이 한 칸씩만 갖기 때문에 새 단위를 넣는 순간 이전 단위는 저절로 풀린다(라디오와 같다).
     */
    public void assignInbUomCd(String uomCd) {
        this.inbUomCd = uomCd;
    }

    public void assignOutbUomCd(String uomCd) {
        this.outbUomCd = uomCd;
    }

    /** 지정 단위 1개가 낱개 몇 개인지. 없는 단위면 예외 — 저장 시점 검증을 통과했다면 나올 수 없다 */
    public long eaQtyOf(String uomCd) {
        return uoms.stream()
                .filter(u -> u.getUomCd().equals(uomCd))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "상품에 등록되지 않은 단위입니다: " + prodCd + " / " + uomCd))
                .getEaQty();
    }

    /**
     * 포장단위 수량을 재고 저장 단위인 낱개(EA)로 환산한다.
     * WMS의 모든 수량 컬럼은 EA이고, OMS 주문 라인만 입력 단위를 유지하므로
     * 호출 지점은 OMS → WMS 경계 세 곳뿐이다 —
     * {@code OmsIbOrderService.confirm()}(발주 → ASN, 입고단위) ·
     * {@code ReceivingService.receiveLine()}(검수 입력, 입고단위) ·
     * {@code OmsOutbOrderService.confirm()}(출고주문 → 창고 출고주문, 출고단위).
     * 이 밖에서 환산을 추가하지 말 것 — 환산 지점이 흩어지면 어느 행이 어느 단위인지
     * 사후에 복원할 방법이 없다.
     */
    public long toEaQty(long qty, String uomCd) {
        return qty * eaQtyOf(uomCd);
    }
}
