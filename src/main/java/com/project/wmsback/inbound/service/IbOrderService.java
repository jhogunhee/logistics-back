package com.project.wmsback.inbound.service;

import com.project.wmsback.inbound.dto.IbLineResponse;
import com.project.wmsback.inbound.dto.IbOrderResponse;
import com.project.wmsback.inbound.dto.IbOrderSearchCond;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inbound.repository.IbOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IbOrderService {

    private final IbOrderRepository ibOrderRepository;
    private final IbLineRepository ibLineRepository;

    public List<IbOrderResponse> list(IbOrderSearchCond cond) {
        List<IbOrder> orders = ibOrderRepository.search(cond);

        // search()가 라인을 fetch join으로 이미 들고 오므로 라인 id는 메모리에서 뽑는다 (추가 조회 없음).
        // 검수일시는 건별로 구하면 그대로 N+1이라 한 번에 받아 주문별로 접는다
        List<Long> ibLineIds = orders.stream()
                .flatMap(o -> o.getLines().stream())
                .map(IbLine::getId)
                .toList();
        Map<Long, LocalDateTime> lastReceiveDtByLine = ibOrderRepository.lastReceiveDtByLine(ibLineIds);

        return orders.stream()
                .map(o -> IbOrderResponse.of(o, lastReceiveDt(o, lastReceiveDtByLine)))
                .toList();
    }

    /** 입고건의 최종 검수일시 = 그 라인들 중 가장 늦은 것 (검수 전이면 null) */
    private LocalDateTime lastReceiveDt(IbOrder order, Map<Long, LocalDateTime> byLine) {
        return order.getLines().stream()
                .map(l -> byLine.get(l.getId()))
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
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
    // (confirm / cancelConfirm). 창고가 예정을 스스로 만들거나 없애면 주문 상태와 어긋나기 때문이다.
    // 취소 가능 여부 판정 자체는 여전히 IbOrder.cancel()이 갖는다 — 규칙의 주인은 ASN이고,
    // 호출 시점만 OMS가 정한다.
}
