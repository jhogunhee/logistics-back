package com.project.wmsback.inventory.repository;

import com.project.wmsback.inventory.dto.InvHistResponse;
import com.project.wmsback.inventory.dto.InvHistSearchCond;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.TxType;

import java.util.List;

public interface InvHistRepositoryCustom {

    /** 라인의 검수 이력(최근 순) — 검수 취소 대상 선택용 */
    List<InvHist> findAllByIbLineIdAndTxTypeOrderByCreatedAtDesc(Long ibLineId, TxType txType);

    /** 재고이력 조회 화면용 검색 (최근 순). MOVE 짝의 로케이션(pairedLocCd)까지 자기 조인으로 함께 채운다 */
    List<InvHistResponse> search(InvHistSearchCond cond);
}