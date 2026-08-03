package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvResponse;
import com.project.wmsback.inventory.dto.InvSearchCond;
import com.project.wmsback.inventory.entity.Inv;

import java.util.List;

public interface InvRepositoryCustom {

    /** 현재고 조회 화면용 검색. 상품+Loc+Lot 조인 결과에 가용수량(보유 − 예약 − 보류)을 계산해 함께 내려준다 */
    List<InvResponse> search(InvSearchCond cond);

    /**
     * 재고조사 라인 생성용 범위 조회. 보관(STORAGE) 재고만 대상이며 세 조건은 모두 선택이다
     * (전부 비면 전 보관 로케이션). 조사 생성 시점의 전산수량을 라인에 스냅샷하기 위해 엔티티로 돌려준다.
     */
    List<Inv> searchStorageByScope(String zonCd, Long locId, Long prodId);
}
