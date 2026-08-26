package com.project.wmsback.inventory.repository;

import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.wmsback.inventory.dto.InvHistResponse;
import com.project.wmsback.inventory.dto.InvHistSearchCond;
import com.project.wmsback.inventory.entity.InvHist;
import com.project.wmsback.inventory.entity.TxTyp;

import java.util.Collection;
import java.util.List;

public interface InvHistRepositoryCustom {

    /** 라인의 검수 이력(최근 순) — 검수 취소 대상 선택용 */
    List<InvHist> findAllByIbLineIdAndTxTypeOrderByCreatedAtDesc(Long ibLineId, TxTyp txTyp);

    /**
     * 여러 라인의 이력을 한 번에(최근 순). 입고건 하나의 검수 이력 탭이 라인마다 조회하면
     * 그대로 N+1이라 라인 id를 모아 한 번에 받는다.
     */
    List<InvHist> findAllByIbLineIdInAndTxTypeOrderByCreatedAtDesc(Collection<Long> ibLineIds, TxTyp txTyp);

    /**
     * 재고이력 조회 화면용 검색 (최근 순, 서버 페이징). MOVE 짝의 로케이션(pairedLocCd)까지 자기 조인으로 함께 채운다.
     * append-only 원장이라 전량 조회를 두지 않는다 — 화면은 페이지 단위로만 가져간다.
     */
    PageResponse<InvHistResponse> search(InvHistSearchCond cond, PageCond pageCond);
}