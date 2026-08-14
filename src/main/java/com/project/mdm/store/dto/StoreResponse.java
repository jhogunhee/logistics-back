package com.project.mdm.store.dto;

import com.project.mdm.store.entity.Store;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class StoreResponse {

    private final Long storeId;
    private final String storeCd;
    private final String storeNm;
    /** 점포그룹·점포유형 (공통코드 STORE_GRP·STORE_TYP). NULL = 미지정 */
    private final String storeGrp;
    private final String storeTyp;
    /** 납품 허용 잔여수명 비율(%). 출고 할당 시 Lot 필터 기준 — 주문 화면이 참고용으로 보여준다 */
    private final Short outbLifeRate;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final String updatedBy;
    private final LocalDateTime updatedAt;

    private StoreResponse(Store store) {
        this.storeId = store.getId();
        this.storeCd = store.getStoreCd();
        this.storeNm = store.getStoreNm();
        this.storeGrp = store.getStoreGrp();
        this.storeTyp = store.getStoreTyp();
        this.outbLifeRate = store.getOutbLifeRate();
        this.createdBy = store.getCreatedBy();
        this.createdAt = store.getCreatedAt();
        this.updatedBy = store.getUpdatedBy();
        this.updatedAt = store.getUpdatedAt();
    }

    public static StoreResponse from(Store store) {
        return new StoreResponse(store);
    }
}
