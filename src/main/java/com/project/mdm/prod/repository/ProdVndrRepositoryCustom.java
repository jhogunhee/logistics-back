package com.project.mdm.prod.repository;

import com.project.mdm.prod.dto.ProdVndrSearchCond;
import com.project.mdm.prod.entity.ProdVndr;

import java.util.List;

public interface ProdVndrRepositoryCustom {

    /**
     * 목록 조회. 자동발주 산정도 이걸 쓴다 — 정렬이 (상품코드, prty, id)라
     * 상품이 바뀌는 첫 행이 곧 대표 벤더다(동률은 id 오름차순이라 결정적).
     */
    List<ProdVndr> search(ProdVndrSearchCond cond);
}
