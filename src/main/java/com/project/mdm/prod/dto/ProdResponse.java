package com.project.mdm.prod.dto;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.ProdUom;
import com.project.mdm.prod.entity.TmpZon;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ProdResponse {

    private final Long prodId;
    private final String prodCd;
    private final String prodNm;
    private final TmpZon tmpZon;
    private final String inbUomCd;
    private final String outbUomCd;
    private final Integer shelfLifeDays;
    /** 상품 이미지 URL (Supabase Storage 퍼블릭 객체). NULL = 이미지 없음 — 화면이 폴백을 그린다 */
    private final String imgUrl;
    /**
     * 입고단위/출고단위 1개 = 낱개(EA) 몇 개 (환산계수). 상품 선택 팝업·검수 화면이 쓴다 —
     * 소비자가 필요로 하는 건 이 두 스칼라뿐이라 포장 배열(uoms)을 통째로 싣지 않는다.
     * 포장 목록 자체는 단위 관리 API(GET /master/prod-uoms)가 준다.
     */
    private final Long inbEaQty;
    private final Long outbEaQty;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private ProdResponse(Prod prod) {
        this.prodId = prod.getId();
        this.prodCd = prod.getProdCd();
        this.prodNm = prod.getProdNm();
        this.tmpZon = prod.getTmpZon();
        this.inbUomCd = prod.getInbUomCd();
        this.outbUomCd = prod.getOutbUomCd();
        this.shelfLifeDays = prod.getShelfLifeDays();
        this.imgUrl = prod.getImgUrl();
        this.inbEaQty = eaQtyOf(prod, prod.getInbUomCd());
        this.outbEaQty = eaQtyOf(prod, prod.getOutbUomCd());
        this.createdBy = prod.getCreatedBy();
        this.createdAt = prod.getCreatedAt();
        this.updatedBy = prod.getUpdatedBy();
        this.updatedAt = prod.getUpdatedAt();
    }

    /** 해당 단위 포장이 아직 없으면 1 — 환산 없음으로 그리는 편이 화면에서 안전하다 (실제 저장은 서버가 검증) */
    private static Long eaQtyOf(Prod prod, String uomCd) {
        return prod.getUoms().stream()
                .filter(u -> u.getUomCd().equals(uomCd))
                .map(ProdUom::getEaQty)
                .findFirst()
                .orElse(1L);
    }

    public static ProdResponse from(Prod prod) {
        return new ProdResponse(prod);
    }
}
