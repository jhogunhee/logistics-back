package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.dto.PutawayCandidateResponse;
import com.project.wmsback.inbound.dto.PutawaySearchCond;
import com.project.wmsback.inbound.entity.IbLine;

import java.util.List;

public interface IbLineRepositoryCustom {

    /** 라인 + SKU를 한 번에 로딩 (라인 응답이 SKU 코드/명/온도대를 쓰므로 N+1 방지) */
    List<IbLine> findAllByOrderIdWithSku(Long ibOrderId);

    /**
     * 적치 대상 (라인, Lot) 배치 전체 — 주문 구분 없이 적치 화면에서 조회.
     * inv_hist를 (ib_line_id, lot_id)로 집계해 스테이징에 남은 수량이 있는 조합만 반환한다.
     */
    List<PutawayCandidateResponse> findAllPendingPutawayBatches(PutawaySearchCond cond);
}