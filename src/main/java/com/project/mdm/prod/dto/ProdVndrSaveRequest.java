package com.project.mdm.prod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.ProdVndr;
import com.project.mdm.vendor.entity.Vendor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * <p>
 * 자기 필드만으로 판정할 수 있는 검사와 엔티티 생성·반영은 여기서 한다({@link #toEntity} · {@link #updateEntity}).
 * DB를 봐야 하는 일(상품·벤더 존재 · 짝 중복)은 서비스 몫이라 상품·벤더를 코드로 받고
 * 서비스가 찾은 엔티티를 넘겨받아 싣는다.
 * <p>
 * 비워 보낸 값은 컬럼 DEFAULT와 같은 값으로 채운다 — JPA는 null을 그대로 INSERT해서 DEFAULT가 걸리지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProdVndrSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    private Long prodVndrId;
    private String prodCd;
    private String vndrCd;
    private Long minQty;
    private Long maxQty;
    private Long minOdrQty;
    private Integer leadDays;
    private Integer prty;

    /** 신규 행 → 엔티티 */
    public ProdVndr toEntity(Prod prod, Vendor vendor) {
        validateFields(prod);
        return ProdVndr.builder()
                .prod(prod)
                .vendor(vendor)
                .minQty(minQty)
                .maxQty(maxQty)
                .minOdrQty(minOdrQtyOrDefault())
                .leadDays(leadDaysOrDefault())
                .prty(prtyOrDefault())
                .build();
    }

    /** 수정 행 → 기존 엔티티에 반영 */
    public void updateEntity(ProdVndr prodVndr, Prod prod, Vendor vendor) {
        validateFields(prod);
        prodVndr.update(prod, vendor, minQty, maxQty,
                minOdrQtyOrDefault(), leadDaysOrDefault(), prtyOrDefault());
    }

    private void validateFields(Prod prod) {
        // DB 제약(ck_prod_vndr_qty)을 커밋 전에 사용자 메시지로 돌려준다
        if (minQty == null || minQty < 0) {
            throw new IllegalArgumentException("발주점은 0 이상 필수입니다: " + prod.getProdCd());
        }
        if (maxQty == null || maxQty < 1) {
            throw new IllegalArgumentException("발주 상한은 1 이상 필수입니다: " + prod.getProdCd());
        }
        if (minQty > maxQty) {
            throw new IllegalArgumentException("발주점은 발주 상한 이하여야 합니다: " + prod.getProdCd());
        }
        if (minOdrQty != null && minOdrQty < 1) {
            throw new IllegalArgumentException("최소주문수량은 1 이상이어야 합니다: " + prod.getProdCd());
        }
        if (leadDays != null && leadDays < 0) {
            throw new IllegalArgumentException("리드타임은 0 이상이어야 합니다: " + prod.getProdCd());
        }
        if (prty != null && prty < 1) {
            throw new IllegalArgumentException("우선순위는 1 이상이어야 합니다: " + prod.getProdCd());
        }
        // 상품에 입고단위 포장이 없으면 발주 수량을 낱개로 환산할 수 없어 산정이 그 상품에서 멈춘다.
        // 저장 시점에 막아 「등록은 되는데 동작하지 않는」 행을 만들지 않는다
        prod.eaQtyOf(prod.getInbUomCd());
    }

    private Long minOdrQtyOrDefault() {
        return minOdrQty != null ? minOdrQty : 1L;
    }

    private Integer leadDaysOrDefault() {
        return leadDays != null ? leadDays : 1;
    }

    private Integer prtyOrDefault() {
        return prty != null ? prty : 1;
    }
}
