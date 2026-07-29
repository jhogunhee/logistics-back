package com.project.wmsback.inbound.controller;

import com.project.wmsback.inbound.dto.IbLineResponse;
import com.project.wmsback.inbound.dto.IbOrderResponse;
import com.project.wmsback.inbound.dto.IbOrderSearchCond;
import com.project.wmsback.inbound.dto.ReceiptResponse;
import com.project.wmsback.inbound.dto.ReceiveRequest;
import com.project.wmsback.inbound.service.IbOrderService;
import com.project.wmsback.inbound.service.ReceivingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/inbound/asns")
@RequiredArgsConstructor
public class IbOrderController {

    private final IbOrderService ibOrderService;
    private final ReceivingService receivingService;

    @GetMapping
    public List<IbOrderResponse> list(@ModelAttribute IbOrderSearchCond cond) {
        return ibOrderService.list(cond);
    }

    // ASN의 생성/취소 엔드포인트는 여기 없다 — 둘 다 OMS 입고주문이 주관한다.
    //   생성: POST /oms/inbound-orders/{id}/confirm
    //   취소: POST /oms/inbound-orders/{id}/confirm-cancel
    // 창고가 예정을 스스로 만들거나 없앨 수 있으면 주문 상태와 어긋나기 때문이다.
    // 여기 남은 것은 조회와 실제 창고 작업(검수/마감)뿐이다.

    @GetMapping("/{ibOrderId}/lines")
    public List<IbLineResponse> lines(@PathVariable Long ibOrderId) {
        return ibOrderService.lines(ibOrderId);
    }

    @PostMapping("/{ibOrderId}/receive")
    public void receive(@PathVariable Long ibOrderId, @RequestBody ReceiveRequest req) {
        receivingService.receive(ibOrderId, req);
    }

    @PostMapping("/{ibOrderId}/close")
    public void close(@PathVariable Long ibOrderId) {
        receivingService.close(ibOrderId);
    }

    @GetMapping("/{ibOrderId}/lines/{ibLineId}/receipts")
    public List<ReceiptResponse> receipts(@PathVariable Long ibOrderId, @PathVariable Long ibLineId) {
        return receivingService.receipts(ibOrderId, ibLineId);
    }

    @PostMapping("/{ibOrderId}/receipts/{invHistId}/cancel")
    public void cancelReceipt(@PathVariable Long ibOrderId, @PathVariable Long invHistId) {
        receivingService.cancelReceipt(ibOrderId, invHistId);
    }
}
