package com.project.wmsback.outbound.service;

import com.project.wmsback.outbound.dto.OutbLineResponse;
import com.project.wmsback.outbound.dto.OutbOrderResponse;
import com.project.wmsback.outbound.dto.OutbOrderSearchCond;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.repository.OutbLineRepository;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 창고 출고주문 조회·취소.
 *
 * <p><b>등록이 없다.</b> 출고주문은 OMS 출고주문 확정({@code OmsOutbOrderService.confirm})으로만
 * 생기고 확정취소로만 사라진다 — 여기에 등록 경로를 두면 원장이 없는 출고가 생겨,
 * 주문 상태와 창고 문서가 서로를 모른 채 갈라진다 (입고예정 ASN과 같은 구조).
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

    /**
     * 취소. 할당 전(CREATED)만 가능 — 상태/웨이브 해제는 엔티티가 처리한다.
     * <p>
     * 상위 주문의 <b>확정취소</b>와는 다른 조작이다: 확정취소는 이 행을 지우고 주문을 작성
     * 상태로 되돌리지만, 취소는 창고 쪽에서만 CANCELLED로 눕히고 주문은 확정으로 남는다.
     */
    @Transactional
    public void cancel(Long outbOrderId) {
        OutbOrder order = outbOrderRepository.findById(outbOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 출고 주문입니다: " + outbOrderId));
        order.cancel();
    }
}
