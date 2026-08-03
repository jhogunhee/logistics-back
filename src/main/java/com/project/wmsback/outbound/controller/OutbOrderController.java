package com.project.wmsback.outbound.controller;

import com.project.wmsback.outbound.dto.OutbLineResponse;
import com.project.wmsback.outbound.dto.OutbOrderResponse;
import com.project.wmsback.outbound.dto.OutbOrderSearchCond;
import com.project.wmsback.outbound.service.OutbOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 창고 출고주문 조회. <b>등록·취소 엔드포인트가 없다</b> — 출고주문은
 * {@code POST /oms/outbound-orders/{id}/confirm}(OMS 주문확정)으로만 생기고
 * {@code /confirm-cancel}(확정취소)로만 사라진다.
 */
@RestController
@RequestMapping("/outbound/orders")
@RequiredArgsConstructor
public class OutbOrderController {

    private final OutbOrderService outbOrderService;

    @GetMapping
    public List<OutbOrderResponse> list(@ModelAttribute OutbOrderSearchCond cond) {
        return outbOrderService.list(cond);
    }

    @GetMapping("/{outbOrderId}/lines")
    public List<OutbLineResponse> lines(@PathVariable Long outbOrderId) {
        return outbOrderService.lines(outbOrderId);
    }
}
