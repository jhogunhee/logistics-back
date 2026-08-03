package com.project.mdm.prod.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.project.mdm.prod.entity.TempZone;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 그리드 일괄 저장 행. status: C(신규) / U(수정) / D(삭제).
 * 신규 행의 상품 코드는 클라이언트에서 받지 않는다 — 서버가 시퀀스로 채번한다.
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
    private TempZone tmpZon;
    /** 발주·납품 단위 (공통코드 UOM). 그 상품의 포장이 없으면 낱개수량 1로 자동 생성된다 */
    private String inbUomCd;
    /** 재고 저장 단위 (공통코드 UOM). 마찬가지로 포장이 없으면 자동 생성된다 */
    private String outbUomCd;
    private Integer shelfLifeDays;

    // 포장 목록(prod_uom)은 여기서 받지 않는다 — 상품 한 건에 여러 행이라 그리드 한 줄에 담기지
    // 않고, 낱개수량·중량은 상품 정보를 고칠 때마다 다시 보낼 값이 아니다. 단위 관리 화면이 맡는다.
}
