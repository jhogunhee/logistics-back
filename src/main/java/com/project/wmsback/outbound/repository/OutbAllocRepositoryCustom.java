package com.project.wmsback.outbound.repository;

import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.outbound.dto.AllocLineResponse;
import com.project.wmsback.outbound.dto.AllocRowResponse;
import com.project.wmsback.outbound.dto.AllocTargetSearchCond;
import com.project.wmsback.outbound.dto.AllocWaveResponse;
import com.project.wmsback.outbound.entity.OutbLine;

import java.util.List;
import java.util.Map;

public interface OutbAllocRepositoryCustom {

    /** 할당 대상 웨이브 목록 — 잔량이 남은 PLANNED 웨이브. 검색조건은 라인이 아니라 웨이브를 거른다 */
    List<AllocWaveResponse> searchTargetWaves(AllocTargetSearchCond cond);

    /** 웨이브의 라인별 주문/할당/잔량 */
    List<AllocLineResponse> lineRows(Long wavId);

    /** 웨이브에 딸린 할당 레코드 목록 (해제 단위) */
    List<AllocRowResponse> allocRows(Long wavId);

    /**
     * 자동할당 대상 라인 — 잔량({@code odr_qty − SUM(aloc_qty)})이 남은 라인만.
     * 상품별 그룹핑과 결정적 처리 순서를 위해 {@code prod_id → expct_de → outb_no → outb_line_id}로 정렬해 돌려준다.
     */
    List<OutbLine> findTargetLines(List<Long> wavIds);

    /** 라인별 기할당 합계 — 잔여요청 계산과 과할당 검증의 기준값 */
    Map<Long, Long> sumAlocQtyByLineIds(List<Long> outbLineIds);

    /**
     * 할당 후보 재고 — 보관(STORAGE) 로케이션의 가용수량 > 0 인 재고.
     * <p><b>정렬은 FEFO다</b>: 유통기한 ASC(NULL 맨 뒤) → 피킹순위 → 로케이션코드 → inv_id.
     * 끝에 id를 붙여 항상 결정적으로 만든다.
     * <p>수동할당 후보 팝업 전용(읽기 전용)이다 — 자동할당은 아래 id 판을 쓴다.
     */
    List<Inv> findCandidates(Long prodId);

    /**
     * 자동할당용 후보 <b>id</b> 목록 (FEFO 순).
     *
     * <p>엔티티가 아니라 id를 돌려주는 이유: 후보를 먼저 엔티티로 읽어두면 영속성 컨텍스트에
     * 올라가고, 그 뒤에 락을 걸어도 <b>이미 읽은 값이 갱신되지 않아</b> 락 이전의 낡은 가용수량을
     * 그대로 쓰게 된다. id만 받아 두고 락을 걸며 처음 읽으면 항상 최신값이다.
     */
    List<Long> findCandidateIds(Long prodId);
}
