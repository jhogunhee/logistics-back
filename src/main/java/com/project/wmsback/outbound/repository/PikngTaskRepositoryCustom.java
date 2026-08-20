package com.project.wmsback.outbound.repository;

import com.project.wmsback.outbound.dto.PickingSearchCond;
import com.project.wmsback.outbound.dto.PickingWaveResponse;
import com.project.wmsback.outbound.dto.PikngRowResponse;
import com.project.wmsback.outbound.dto.PikngTaskSearchCond;
import com.project.wmsback.outbound.dto.PikngWaveDetailResponse;
import com.project.wmsback.outbound.dto.PikngWaveResponse;

import java.util.List;

public interface PikngTaskRepositoryCustom {

    /**
     * 피킹지시 화면의 웨이브 목록 — 할당이 1건 이상 있는 PLANNED(발행 대상) + ISSUED 전부(확인·취소).
     * 검색조건은 할당 화면과 같은 EXISTS — 라인이 아니라 웨이브를 거르고 합계는 언제나 웨이브 전체다.
     */
    List<PikngWaveResponse> searchTaskWaves(PikngTaskSearchCond cond);

    /**
     * 발행 전(PLANNED) 웨이브의 지시 대상 행 — 할당 단위. 발행 시와 같은 순서
     * (loc.pikng_prty → loc_cd → alloc id)로 정렬해 발행 미리보기가 되게 한다.
     */
    List<PikngRowResponse> allocRowsForIssue(Long wavId);

    /**
     * 발행 후(ISSUED) 웨이브의 지시 행 — 스냅샷 기준, srt_seq 순(= 집품 동선).
     * alloc → inv 조인을 쓰지 않는다 — 완료된 지시는 재고 행이 삭제됐을 수 있다.
     */
    List<PikngRowResponse> taskRows(Long wavId);

    /** 할당이 0건이라 발행을 막는 주문 — 라인 목록에 나타나지 않으므로 별도로 내려 화면이 설명한다 */
    List<PikngWaveDetailResponse.NoAllocOrder> noAllocOrders(Long wavId);

    /** 피킹 화면의 웨이브 목록 — ISSUED 웨이브의 살아 있는 지시 합계 (잔량 0도 당일 확인용으로 남긴다) */
    List<PickingWaveResponse> searchPickingWaves(PickingSearchCond cond);
}
