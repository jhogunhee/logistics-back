package com.project.wmsback.outbound.service;

import com.project.wmsback.outbound.dto.OutbLineResponse;
import com.project.wmsback.outbound.dto.OutbOrderResponse;
import com.project.wmsback.outbound.dto.OutbOrderSearchCond;
import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.repository.OutbAllocRepository;
import com.project.wmsback.outbound.repository.OutbLineRepository;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 창고 출고주문 조회.
 *
 * <p><b>등록도 취소도 없다.</b> 출고주문은 OMS 출고주문 확정({@code OmsOutbOrderService.confirm})으로만
 * 생기고 확정취소로만 사라진다 — 여기에 그 경로를 두면 원장이 없는 출고가 생기거나, 주문은
 * 확정인데 창고 문서만 죽은 상태가 남아 둘이 서로를 모른 채 갈라진다 (입고예정 ASN과 같은 구조).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutbOrderService {

    private final OutbOrderRepository outbOrderRepository;
    private final OutbLineRepository outbLineRepository;
    /**
     * 할당 수량 집계용. 입고는 검수 수량이 {@code ib_line} 컬럼이라 라인만 읽으면 끝나지만,
     * 출고는 할당 수량을 라인에 두지 않아(수량-상태 불일치 차단) 여기서 집계를 한 번 더 읽는다.
     */
    private final OutbAllocRepository outbAllocRepository;

    public List<OutbOrderResponse> list(OutbOrderSearchCond cond) {
        List<OutbOrder> orders = outbOrderRepository.search(cond);
        // 주문마다 집계를 돌리면 N+1이 된다 — 전 주문의 라인 id를 모아 한 번에 읽는다
        Map<Long, Long> alocByLineId = outbAllocRepository.sumAlocQtyByLineIds(
                orders.stream().flatMap(o -> o.getLines().stream()).map(OutbLine::getId).toList());

        return orders.stream()
                .map(o -> OutbOrderResponse.from(o, alocByLineId))
                .toList();
    }

    public List<OutbLineResponse> lines(Long outbOrderId) {
        if (!outbOrderRepository.existsById(outbOrderId)) {
            throw new IllegalArgumentException("존재하지 않는 출고 주문입니다: " + outbOrderId);
        }
        List<OutbLine> lines = outbLineRepository.findAllByOutbOrderIdWithProd(outbOrderId);
        Map<Long, Long> alocByLineId = outbAllocRepository.sumAlocQtyByLineIds(
                lines.stream().map(OutbLine::getId).toList());

        return lines.stream()
                .map(l -> OutbLineResponse.from(l, alocByLineId.getOrDefault(l.getId(), 0L)))
                .toList();
    }
}
