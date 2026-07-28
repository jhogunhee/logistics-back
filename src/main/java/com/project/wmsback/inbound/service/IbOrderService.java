package com.project.wmsback.inbound.service;

import com.project.wmsback.inbound.dto.IbLineResponse;
import com.project.wmsback.inbound.dto.IbOrderResponse;
import com.project.wmsback.inbound.dto.IbOrderSearchCond;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inbound.repository.IbOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IbOrderService {

    private final IbOrderRepository ibOrderRepository;
    private final IbLineRepository ibLineRepository;

    public List<IbOrderResponse> list(IbOrderSearchCond cond) {
        return ibOrderRepository.search(cond).stream()
                .map(IbOrderResponse::from)
                .toList();
    }

    public List<IbLineResponse> lines(Long ibOrderId) {
        if (!ibOrderRepository.existsById(ibOrderId)) {
            throw new IllegalArgumentException("존재하지 않는 입고예정입니다: " + ibOrderId);
        }
        return ibLineRepository.findAllByOrderIdWithProd(ibOrderId).stream()
                .map(IbLineResponse::from)
                .toList();
    }

    // ASN의 생성도 취소도 여기 없다. 둘 다 OmsIbOrderService가 주문 상태 전이와 한 트랜잭션에서 처리한다
    // (convert / cancelConvert). 창고가 예정을 스스로 만들거나 없애면 주문 상태와 어긋나기 때문이다.
    // 취소 가능 여부 판정 자체는 여전히 IbOrder.cancel()이 갖는다 — 규칙의 주인은 ASN이고,
    // 호출 시점만 OMS가 정한다.
}
