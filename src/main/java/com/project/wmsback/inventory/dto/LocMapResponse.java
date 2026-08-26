package com.project.wmsback.inventory.dto;

import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.warehouse.entity.BizDvsn;

/**
 * 로케이션 점유 맵 1칸 = STORAGE 로케이션 1개.
 * 점유율(onHandQty ÷ maxQty)은 프론트가 파생하지만 보충 미달(fxngShort)과 적재가능수량(availQty)은
 * 서버가 판정한다 — 적재가능은 LocCapacityService가 소유한 식이라 화면이 다시 계산하면 갈라진다.
 * fxngShort도 같은 이유다 —
 * 정기보충 산정(SpmtService.plan)과 같은 식(지정 상품 현재고 + 지정 상품 유입 < min)이어야 하고,
 * 프론트가 따로 계산하면 두 화면이 갈라진다(유입을 모르는 지도는 보충지시가 이미 뜬 자리도 미달로 칠했다).
 * fxng* 필드는 고정상품 미지정 자리면 전부 null, 지정 자리면 fxngOnHandQty·fxngInflowQty는 없어도 0이다.
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
        /** 전 상품 미완료 유입 잔량 (적치지시 + 이동지시). 아래 fxngInflowQty는 고정 지정 상품분만이라 다른 값이다 */
        long inflowQty,
        /** 적재가능수량 = maxQty − onHandQty − inflowQty. null = 상한 없음(무제한) */
        Long availQty,
        String fxngProdCd,
        String fxngProdNm,
        /** 고정 지정 상품의 이미지 URL. 미지정 자리거나 이미지가 없으면 null */
        String fxngProdImgUrl,
        Long fxngMinQty,
        Long fxngOnHandQty,
        Long fxngInflowQty,
        Boolean fxngShort
) {
}
