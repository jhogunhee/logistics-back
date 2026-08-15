package com.project.mdm.prod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.ProdUom;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 단위(상품 포장) 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * <p>
 * 상품과 단위 코드는 (prod_id, uom_cd) 유일키를 이루므로 등록 후 바꿀 수 없다 —
 * 바꾸려면 지우고 다시 넣는다. 수정 대상은 낱개수량과 중량뿐이다.
 * <p>
 * 자기 필드만으로 판정할 수 있는 검사와 포장 생성·반영은 여기서 한다({@link #toEntity} · {@link #updateEntity}).
 * DB를 봐야 하는 일(단위 중복 · 역할 이동과 낱개수량 변경 가드 · 삭제 가드)은 서비스 몫이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProdUomSaveRequest {

    /** 그리드 행 상태 (C/U/D). JSON 필드명은 프론트 그리드 관례대로 _status */
    @JsonProperty("_status")
    private String status;

    /** 수정·삭제 대상 식별자 (신규 행은 비어 있다) */
    private Long prodUomId;

    /** 신규 행에서만 쓴다 */
    private Long prodId;

    /** 신규 행에서만 쓴다. 공통코드 UOM 그룹의 코드값 */
    private String uomCd;

    /** 이 단위 1개 = 낱개 몇 개 */
    private Long eaQty;

    /** 이 단위 1개의 중량(kg). 미측정이면 비워둔다 */
    private BigDecimal wgt;

    /**
     * 이 포장을 상품의 입고단위로 지정할지. 상품이 입고단위를 한 칸만 갖기 때문에
     * 화면에서는 상품 안의 라디오로 동작하고, 서버는 <b>true인 행만</b> 반영한다 —
     * 새 단위를 넣으면 이전 단위는 저절로 풀리므로 false를 따로 처리할 필요가 없다.
     */
    private Boolean inbUom;

    /** 이 포장을 상품의 출고단위(=재고 저장 단위)로 지정할지. 입고단위와 독립이다 */
    private Boolean outbUom;

    /** 신규 행 → 포장을 만들어 상품에 붙인다. 상품은 서비스가 찾아 넘긴다 */
    public ProdUom toEntity(Prod prod) {
        if (uomCd == null || uomCd.isBlank()) {
            throw new IllegalArgumentException("단위는 필수입니다.");
        }
        validateQty(prod.getProdNm() + " / " + uomCd);
        ProdUom uom = ProdUom.builder()
                .uomCd(uomCd)
                .eaQty(eaQty)
                .wgt(wgt)
                .build();
        prod.addUom(uom);
        return uom;
    }

    /** 수정 행 → 기존 포장에 반영. 낱개수량과 중량만 고친다 */
    public void updateEntity(ProdUom uom) {
        validateQty(uom.getProd().getProdNm() + " / " + uom.getUomCd());
        uom.update(eaQty, wgt);
    }

    private void validateQty(String label) {
        // 낱개수량이 0이나 음수면 환산이 수량을 0으로 만들거나 부호를 뒤집는다
        if (eaQty == null || eaQty < 1) {
            throw new IllegalArgumentException("낱개수량은 1 이상이어야 합니다: " + label);
        }
        // 미측정(NULL)은 허용하되 0이나 음수 중량은 실측값일 수 없다
        if (wgt != null && wgt.signum() <= 0) {
            throw new IllegalArgumentException("중량은 비워두거나(미측정) 0보다 커야 합니다: " + label);
        }
    }
}
