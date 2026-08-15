package com.project.mdm.prod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * 신규 행의 상품 코드는 클라이언트에서 받지 않는다 — 서버가 시퀀스로 채번한다.
 * <p>
 * 자기 필드만으로 판정할 수 있는 검사와 엔티티 생성·반영은 여기서 한다({@link #toEntity} · {@link #applyTo}).
 * DB를 봐야 하는 일(채번 · 삭제 참조 검사)은 서비스 몫이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProdSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    private Long prodId;
    private String prodNm;
    private TmpZon tmpZon;
    /** 발주·납품 단위 (공통코드 UOM). 신규 등록 시 그 포장이 없으면 낱개수량 1로 자동 생성된다. 등록 후 변경은 단위 관리 화면 */
    private String inbUomCd;
    /** 출고주문 단위 (공통코드 UOM). 마찬가지로 신규 등록 시에만 자동 생성되고 등록 후엔 못 바꾼다 */
    private String outbUomCd;
    private Integer shelfLifeDays;

    // 포장 목록(prod_uom)은 여기서 받지 않는다 — 상품 한 건에 여러 행이라 그리드 한 줄에 담기지
    // 않고, 낱개수량·중량은 상품 정보를 고칠 때마다 다시 보낼 값이 아니다. 단위 관리 화면이 맡는다.

    /** 신규 행 → 엔티티. 상품 코드는 서비스가 채번해 넘긴다. 단위 필수 검사는 신규에만 있다 */
    public Prod toEntity(String prodCd) {
        validateFields();
        requireUomCd(inbUomCd, "입고단위");
        requireUomCd(outbUomCd, "출고단위");
        Prod prod = Prod.builder()
                .prodCd(prodCd)
                .prodNm(prodNm)
                .tmpZon(tmpZon)
                .inbUomCd(inbUomCd)
                .outbUomCd(outbUomCd)
                .shelfLifeDays(shelfLifeDays)
                .build();
        prod.ensureRoleUoms();
        return prod;
    }

    /** 수정 행 → 기존 엔티티에 반영. 입고/출고단위는 보지 않는다 — 등록 후 변경은 단위 관리 화면이 맡는다 */
    public void applyTo(Prod prod) {
        validateFields();
        prod.update(prodNm, tmpZon, shelfLifeDays);
    }

    private void validateFields() {
        if (prodNm == null || prodNm.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다.");
        }
        if (tmpZon == null) {
            throw new IllegalArgumentException("온도대는 필수입니다: " + prodNm);
        }
        // NULL = 유통기한 미관리(공산품 등). 값이 있으면 1 이상이어야 한다.
        if (shelfLifeDays != null && shelfLifeDays < 1) {
            throw new IllegalArgumentException("유통기한(일)은 비워두거나(미관리) 1 이상이어야 합니다: " + prodNm);
        }
    }

    /** 빈 값만 막는다 — 공통코드 UOM에 실재하는지는 화면 콤보박스가 보장한다 */
    private void requireUomCd(String uomCd, String label) {
        if (uomCd == null || uomCd.isBlank()) {
            throw new IllegalArgumentException(label + "는 필수입니다: " + prodNm);
        }
    }
}
