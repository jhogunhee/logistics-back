package com.project.mdm.store.dto;

import com.project.mdm.store.entity.Store;
import lombok.Getter;

@Getter
public class StoreResponse {

    private final Long storeId;
    private final String storeCd;
    private final String storeNm;
    /** 납품 허용 잔여수명 비율(%). 출고 할당 시 Lot 필터 기준 — 주문 화면이 참고용으로 보여준다 */
    private final Short outbLifeRate;

    private StoreResponse(Store store) {
        this.storeId = store.getId();
        this.storeCd = store.getStoreCd();
        this.storeNm = store.getStoreNm();
        this.outbLifeRate = store.getOutbLifeRate();
    }

    public static StoreResponse from(Store store) {
        return new StoreResponse(store);
    }
}
