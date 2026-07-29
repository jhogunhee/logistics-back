package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.ProdSearchCond;
import com.project.wmsback.master.entity.Prod;

import java.util.List;
import java.util.Optional;

public interface ProdRepositoryCustom {

    List<Prod> search(ProdSearchCond cond);

    /**
     * Lot 채번(상품+입고일자 단위 리셋) 직렬화용 로우 락.
     * 같은 상품에 대해 동시에 검수가 들어와도 "기존 Lot 조회 → 건수 세기 → 채번" 구간이 겹치지 않도록 한다.
     */
    Optional<Prod> findByIdForUpdate(Long id);

    /**
     * 이 상품을 참조하는 곳의 이름(예: "재고"). 없으면 {@code null} — 삭제 가드가 쓴다.
     * <p>
     * FK가 0건이라 DB가 막아주지 않는다. 그냥 지우면 재고·이력·주문 라인이 없는 상품을
     * 가리키게 되어 조회에서 상품명이 비고, 그 데이터를 되살릴 방법이 없다.
     * <p>
     * 포장({@code prod_uom})은 세지 않는다 — 상품에 종속된 것이라 함께 지우는 게 맞고,
     * {@code Prod.uoms}의 cascade가 처리한다.
     */
    String findFirstReference(Long prodId);
}