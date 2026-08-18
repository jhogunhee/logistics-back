package com.project.wmsback.inbound.repository;

import com.project.wmsback.inbound.dto.IbOrderCfmResponse;
import com.project.wmsback.inbound.dto.IbOrderInspResponse;
import com.project.wmsback.inbound.dto.IbOrderResponse;
import com.project.wmsback.inbound.dto.IbOrderSearchCond;

import java.util.List;

/**
 * 입고건 목록은 화면별로 세 벌이다. 엔티티가 아니라 응답을 바로 뽑는다 — 라인 수량 집계 ·
 * 최종 검수일시 · 5단계 진행이 전부 SQL에서 계산되므로 서비스가 뒤에 붙일 것이 없다.
 * <p>
 * 셋으로 나눈 기준은 뽑는 컬럼이 아니라 <b>쿼리 모양</b>이다 — 자세한 것은 구현체 주석 참고.
 */
public interface IbOrderRepositoryCustom {

    /** 입고예정(ASN) 관리 · 대시보드 */
    List<IbOrderResponse> search(IbOrderSearchCond cond);

    /** 입고검수 · 검수정책 시뮬레이션 — 진행단계를 만들지 않는다 */
    List<IbOrderInspResponse> searchForInsp(IbOrderSearchCond cond);

    /** 입고확정 — 최종 검수일시를 만들지 않는다 */
    List<IbOrderCfmResponse> searchForCfm(IbOrderSearchCond cond);
}
