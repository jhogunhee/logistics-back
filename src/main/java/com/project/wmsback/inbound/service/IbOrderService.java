package com.project.wmsback.inbound.service;

import com.project.wmsback.inbound.dto.IbLineResponse;
import com.project.wmsback.inbound.dto.IbOrderCfmResponse;
import com.project.wmsback.inbound.dto.IbOrderInspResponse;
import com.project.wmsback.inbound.dto.IbOrderResponse;
import com.project.wmsback.inbound.dto.IbOrderSearchCond;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inbound.repository.IbOrderRepository;
import com.project.wmsback.inbound.repository.PutawayTaskQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IbOrderService {

    private final IbOrderRepository ibOrderRepository;
    private final IbLineRepository ibLineRepository;
    private final PutawayTaskQueryRepository putawayTaskQueryRepository;

    // 목록은 화면별로 세 벌이다 — 수량 집계 · 최종 검수일시 · 진행 파생과 필터가 전부 쿼리 안에 있어
    // 서비스가 뒤에 붙일 것이 없다. 왜 하나로 합치지 않았는지는 IbOrderRepositoryImpl 주석 참고.

    /** 입고예정(ASN) 관리 · 대시보드 */
    public List<IbOrderResponse> list(IbOrderSearchCond cond) {
        return ibOrderRepository.search(cond);
    }

    /** 입고검수 · 검수정책 시뮬레이션 */
    public List<IbOrderInspResponse> listForInsp(IbOrderSearchCond cond) {
        return ibOrderRepository.searchForInsp(cond);
    }

    /** 입고확정 */
    public List<IbOrderCfmResponse> listForCfm(IbOrderSearchCond cond) {
        return ibOrderRepository.searchForCfm(cond);
    }

    public List<IbLineResponse> lines(Long ibOrderId) {
        if (!ibOrderRepository.existsById(ibOrderId)) {
            throw new IllegalArgumentException("존재하지 않는 입고예정입니다: " + ibOrderId);
        }
        List<IbLine> lines = ibLineRepository.findAllByOrderIdWithProd(ibOrderId);
        // 진행단계가 「지시가 나갔는가」를 보므로 라인 id를 모아 한 번에 묻는다 (라인마다 물으면 N+1)
        Set<Long> openDrctLineIds = putawayTaskQueryRepository.openIbLineIds(
                lines.stream().map(IbLine::getId).toList());
        return lines.stream()
                .map(line -> IbLineResponse.from(line, openDrctLineIds.contains(line.getId())))
                .toList();
    }

    // ASN의 생성도 취소도 여기 없다. 둘 다 OmsIbOrderService가 주문 상태 전이와 한 트랜잭션에서 처리한다
    // (confirm / cancelConfirm). 창고가 예정을 스스로 만들거나 없애면 주문 상태와 어긋나기 때문이다.
    // 취소 가능 여부 판정 자체는 여전히 IbOrder.cancel()이 갖는다 — 규칙의 주인은 ASN이고,
    // 호출 시점만 OMS가 정한다.
}
