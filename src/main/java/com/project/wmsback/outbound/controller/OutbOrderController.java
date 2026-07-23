package com.project.wmsback.outbound.controller;

import com.project.wmsback.outbound.dto.OutbLineResponse;
import com.project.wmsback.outbound.dto.OutbOrderCreateRequest;
import com.project.wmsback.outbound.dto.OutbOrderResponse;
import com.project.wmsback.outbound.dto.OutbOrderSearchCond;
import com.project.wmsback.outbound.service.OutbOrderService;
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
@RequestMapping("/outbound/orders")
@RequiredArgsConstructor
public class OutbOrderController {

    private final OutbOrderService outbOrderService;

    @GetMapping
    public List<OutbOrderResponse> list(@ModelAttribute OutbOrderSearchCond cond) {
        return outbOrderService.list(cond);
    }

    @PostMapping
    public Long create(@RequestBody OutbOrderCreateRequest req) {
        return outbOrderService.create(req);
    }

    @GetMapping("/{outbOrderId}/lines")
    public List<OutbLineResponse> lines(@PathVariable Long outbOrderId) {
        return outbOrderService.lines(outbOrderId);
    }

    @PostMapping("/{outbOrderId}/cancel")
    public void cancel(@PathVariable Long outbOrderId) {
        outbOrderService.cancel(outbOrderId);
    }
}
