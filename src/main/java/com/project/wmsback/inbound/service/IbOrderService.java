package com.project.wmsback.inbound.service;

import com.project.wmsback.inbound.dto.IbLineResponse;
import com.project.wmsback.inbound.dto.IbOrderCreateRequest;
import com.project.wmsback.inbound.dto.IbOrderResponse;
import com.project.wmsback.inbound.dto.IbOrderSearchCond;
import com.project.wmsback.inbound.entity.IbLine;
import com.project.wmsback.inbound.entity.IbOrder;
import com.project.wmsback.inbound.repository.IbLineRepository;
import com.project.wmsback.inbound.repository.IbOrderRepository;
import com.project.wmsback.master.entity.Sku;
import com.project.wmsback.master.repository.SkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IbOrderService {

    private final IbOrderRepository ibOrderRepository;
    private final IbLineRepository ibLineRepository;
    private final SkuRepository skuRepository;

    public List<IbOrderResponse> list(IbOrderSearchCond cond) {
        return ibOrderRepository.search(cond).stream()
                .map(IbOrderResponse::from)
                .toList();
    }

    public List<IbLineResponse> lines(Long ibOrderId) {
        if (!ibOrderRepository.existsById(ibOrderId)) {
            throw new IllegalArgumentException("존재하지 않는 입고예정입니다: " + ibOrderId);
        }
        return ibLineRepository.findAllByOrderIdWithSku(ibOrderId).stream()
                .map(IbLineResponse::from)
                .toList();
    }

    /** ASN 등록. 입고번호는 예정일 + 시퀀스로 채번 (예: IB-20260717-001) */
    @Transactional
    public Long create(IbOrderCreateRequest req) {
        validate(req);
        String ibNo = String.format("IB-%s-%03d",
                req.getExpctDt().format(DateTimeFormatter.BASIC_ISO_DATE),
                ibOrderRepository.nextIbNoSeq());

        IbOrder order = IbOrder.builder()
                .ibNo(ibNo)
                .vndrNm(req.getVndrNm())
                .expctDt(req.getExpctDt())
                .build();
        for (IbOrderCreateRequest.LineRequest line : req.getLines()) {
            Sku sku = skuRepository.findById(line.getSkuId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 SKU입니다: " + line.getSkuId()));
            order.addLine(IbLine.builder()
                    .sku(sku)
                    .expctQty(line.getExpctQty())
                    .build());
        }
        ibOrderRepository.save(order); // cascade로 라인까지 함께 저장
        return order.getId();
    }

    /** 취소. 검수 시작 전(SCHEDULED)만 가능 — 상태 검증은 엔티티가 한다 */
    @Transactional
    public void cancel(Long ibOrderId) {
        IbOrder order = ibOrderRepository.findById(ibOrderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 입고예정입니다: " + ibOrderId));
        order.cancel();
    }

    private void validate(IbOrderCreateRequest req) {
        if (req.getVndrNm() == null || req.getVndrNm().isBlank()) {
            throw new IllegalArgumentException("벤더명은 필수입니다.");
        }
        if (req.getExpctDt() == null) {
            throw new IllegalArgumentException("입고 예정일은 필수입니다.");
        }
        if (req.getLines() == null || req.getLines().isEmpty()) {
            throw new IllegalArgumentException("입고 라인은 최소 1건 필요합니다.");
        }
        for (IbOrderCreateRequest.LineRequest line : req.getLines()) {
            if (line.getSkuId() == null) {
                throw new IllegalArgumentException("라인의 SKU는 필수입니다.");
            }
            if (line.getExpctQty() == null || line.getExpctQty() < 1) {
                throw new IllegalArgumentException("입고 예정 수량은 1 이상이어야 합니다.");
            }
        }
    }
}
