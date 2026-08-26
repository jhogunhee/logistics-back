package com.project.wmsback.inventory.repository;

import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.wmsback.inventory.dto.InvHldAcrstResponse;
import com.project.wmsback.inventory.dto.InvHldAcrstSearchCond;

public interface InvHldAcrstRepositoryCustom {

    /** 보류 등록 실적 (최근 순, 서버 페이징). append-only 로그라 전량 조회를 두지 않는다 */
    PageResponse<InvHldAcrstResponse> search(InvHldAcrstSearchCond cond, PageCond pageCond);
}
