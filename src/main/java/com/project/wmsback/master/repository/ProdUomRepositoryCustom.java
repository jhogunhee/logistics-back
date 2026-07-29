package com.project.wmsback.master.repository;

import com.project.wmsback.master.dto.ProdUomSearchCond;
import com.project.wmsback.master.entity.ProdUom;

import java.util.List;

public interface ProdUomRepositoryCustom {

    /** 단위 관리 화면 목록. 상품코드·상품명을 함께 보여주므로 상품을 조인해 온다 */
    List<ProdUom> search(ProdUomSearchCond cond);
}
