package com.project.mdm.prod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 단위(상품 포장) 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * <p>
 * 상품과 단위 코드는 (prod_id, uom_cd) 유일키를 이루므로 등록 후 바꿀 수 없다 —
 * 바꾸려면 지우고 다시 넣는다. 수정 대상은 낱개수량과 중량뿐이다.
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
}
