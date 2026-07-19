package com.project.wmsback.inbound.controller;

import com.project.wmsback.inbound.dto.IbLineResponse;
import com.project.wmsback.inbound.dto.IbOrderCreateRequest;
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

    @PostMapping
    public Long create(@RequestBody IbOrderCreateRequest req) {
        return ibOrderService.create(req);
    }

    @GetMapping("/{ibOrderId}/lines")
    public List<IbLineResponse> lines(@PathVariable Long ibOrderId) {
        return ibOrderService.lines(ibOrderId);
    }

    @PostMapping("/{ibOrderId}/cancel")
    public void cancel(@PathVariable Long ibOrderId) {
        ibOrderService.cancel(ibOrderId);
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
