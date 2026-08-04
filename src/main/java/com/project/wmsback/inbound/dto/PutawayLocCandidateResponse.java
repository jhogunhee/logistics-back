package com.project.wmsback.inbound.dto;

import com.project.wmsback.warehouse.entity.Loc;
import lombok.Getter;

/**
 * 수동 적치지시의 로케이션 후보. 전략이 없거나 추천이 남긴 잔량을 사람이 직접 배정할 때 쓴다.
 * 적재가능수량을 함께 내려주는 이유는 고른 뒤 생성 시점 검증에서 튕기는 일을 줄이기 위해서다.
 */
@Getter
public class PutawayLocCandidateResponse {

    private final Long locId;
    private final String locCd;
    private final String zonCd;
    private final Integer pikngPrty;
    /** 적재가능수량. null = 최대 적재 수량 미설정(무제한) */
    private final Long availQty;

    private PutawayLocCandidateResponse(Loc loc, Long availQty) {
        this.locId = loc.getId();
        this.locCd = loc.getLocCd();
        this.zonCd = loc.getZonCd();
        this.pikngPrty = loc.getPikngPrty();
        this.availQty = availQty;
    }

    public static PutawayLocCandidateResponse of(Loc loc, Long availQty) {
        return new PutawayLocCandidateResponse(loc, availQty);
    }
}
