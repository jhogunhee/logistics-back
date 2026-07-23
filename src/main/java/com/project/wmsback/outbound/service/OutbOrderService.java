package com.project.wmsback.outbound.service;

import com.project.wmsback.master.entity.Sku;
import com.project.wmsback.master.entity.Store;
import com.project.wmsback.master.repository.SkuRepository;
import com.project.wmsback.master.repository.StoreRepository;
import com.project.wmsback.outbound.dto.OutbLineResponse;
import com.project.wmsback.outbound.dto.OutbOrderCreateRequest;
import com.project.wmsback.outbound.dto.OutbOrderResponse;
import com.project.wmsback.outbound.dto.OutbOrderSearchCond;
import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.repository.OutbLineRepository;
import com.project.wmsback.outbound.repository.OutbOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutbOrderService {

    private final OutbOrderRepository outbOrderRepository;
    private final OutbLineRepository outbLineRepository;
    private final StoreRepository storeRepository;
    private final SkuRepository skuRepository;

    public List<OutbOrderResponse> list(OutbOrderSearchCond cond) {
        return outbOrderRepository.search(cond).stream()
                .map(OutbOrderResponse::from)
                .toList();
    }

    public List<OutbLineResponse> lines(Long outbOrderId) {
        if (!outbOrderRepository.existsById(outbOrderId)) {
            throw new IllegalArgumentException("존재하지 않는 출고 주문입니다: " + outbOrderId);
        }
        return outbLineRepository.findAllByOutbOrderIdWithSku(outbOrderId).stream()
                .map(OutbLineResponse::from)
                .toList();
    }

    /** 출고 주문 등록. 출고번호는 주문일 + 시퀀스로 채번 (예: OB-20260718-001) */
    @Transactional
    public Long create(OutbOrderCreateRequest req) {
        validate(req);
        Store store = storeRepository.findById(req.getStoreId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 점포입니다: " + req.getStoreId()));

        String outbNo = String.format("OB-%s-%03d",
                req.getOrderDt().format(DateTimeFormatter.BASIC_ISO_DATE),
                outbOrderRepository.nextOutbNoSeq());

        OutbOrder order = OutbOrder.builder()
                .outbNo(outbNo)
                .store(store)
                .orderDt(req.getOrderDt())
                .build();
        for (OutbOrderCreateRequest.LineRequest line : req.getLines()) {
            Sku sku = skuRepository.findById(line.getSkuId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 SKU입니다: " + line.getSkuId()));
            order.addLine(OutbLine.builder()
                    .sku(sku)
                    .orderQty(line.getOrderQty())
                    .build());
        }
        outbOrderRepository.save(order); // cascade로 라인까지 함께 저장
        return order.getId();
    }

    /** 취소. 할당 전(CREATED)만 가능 — 상태/웨이브 해제는 엔티티가 처리한다 */
    @Transactional
    public void cancel(Long outbOrderId) {
        OutbOrder order = outbOrderRepository.findById(outbOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 출고 주문입니다: " + outbOrderId));
        order.cancel();
    }

    private void validate(OutbOrderCreateRequest req) {
        if (req.getStoreId() == null) {
            throw new IllegalArgumentException("출고처 점포는 필수입니다.");
        }
        if (req.getOrderDt() == null) {
            throw new IllegalArgumentException("주문일은 필수입니다.");
        }
        if (req.getLines() == null || req.getLines().isEmpty()) {
            throw new IllegalArgumentException("출고 라인은 최소 1건 필요합니다.");
        }
        for (OutbOrderCreateRequest.LineRequest line : req.getLines()) {
            if (line.getSkuId() == null) {
                throw new IllegalArgumentException("라인의 SKU는 필수입니다.");
            }
            if (line.getOrderQty() == null || line.getOrderQty() < 1) {
                throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다.");
            }
        }
    }
}
