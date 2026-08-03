package com.project.wmsback.outbound.service;

import com.project.wmsback.outbound.dto.OutbLineResponse;
import com.project.wmsback.outbound.dto.OutbOrderResponse;
import com.project.wmsback.outbound.dto.OutbOrderSearchCond;
import com.project.wmsback.outbound.repository.OutbLineRepository;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public List<OutbOrderResponse> list(OutbOrderSearchCond cond) {
        return outbOrderRepository.search(cond).stream()
                .map(OutbOrderResponse::from)
                .toList();
    }

    public List<OutbLineResponse> lines(Long outbOrderId) {
        if (!outbOrderRepository.existsById(outbOrderId)) {
            throw new IllegalArgumentException("존재하지 않는 출고 주문입니다: " + outbOrderId);
        }
        return outbLineRepository.findAllByOutbOrderIdWithProd(outbOrderId).stream()
                .map(OutbLineResponse::from)
                .toList();
    }
}
