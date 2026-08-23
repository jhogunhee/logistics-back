package com.project.wmsback.inventory.dto;

import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.warehouse.entity.BizDvsn;

/**
 * 로케이션 점유 맵 1칸 = STORAGE 로케이션 1개.
 * 점유율(onHandQty ÷ maxQty)과 보충 미달(fxngOnHandQty &lt; fxngMinQty)은 프론트가 파생한다.
 * fxng* 필드는 고정상품 미지정 자리면 전부 null, 지정 자리면 fxngOnHandQty는 재고가 없어도 0이다.
 */
public record LocMapResponse(
        Long locId,
        String locCd,
        String zonCd,
        String zonNm,
        BizDvsn bizDvsn,
        TmpZon tmpZon,
        Long maxQty,
        long onHandQty,
        long alocQty,
        long hldQty,
        String fxngProdCd,
        String fxngProdNm,
        /** 고정 지정 상품의 이미지 URL. 미지정 자리거나 이미지가 없으면 null */
        String fxngProdImgUrl,
        Long fxngMinQty,
        Long fxngOnHandQty
) {
}
