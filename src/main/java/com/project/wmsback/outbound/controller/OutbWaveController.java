package com.project.wmsback.outbound.controller;

import com.project.wmsback.outbound.dto.OutbWaveOrdersRequest;
import com.project.wmsback.outbound.dto.OutbWaveResponse;
import com.project.wmsback.outbound.dto.OutbWaveSearchCond;
import com.project.wmsback.outbound.service.OutbWaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/outbound/waves")
@RequiredArgsConstructor
public class OutbWaveController {

    private final OutbWaveService outbWaveService;

    @GetMapping
    public List<OutbWaveResponse> list(@ModelAttribute OutbWaveSearchCond cond) {
        return outbWaveService.list(cond);
    }

    @PostMapping
    public Long create(@RequestBody OutbWaveOrdersRequest req) {
        return outbWaveService.create(req);
    }

    @GetMapping("/{wavId}")
    public OutbWaveResponse detail(@PathVariable Long wavId) {
        return outbWaveService.detail(wavId);
    }

    @PostMapping("/{wavId}/orders")
    public void addOrders(@PathVariable Long wavId, @RequestBody OutbWaveOrdersRequest req) {
        outbWaveService.addOrders(wavId, req);
    }

    @DeleteMapping("/{wavId}/orders/{outbOrderId}")
    public void removeOrder(@PathVariable Long wavId, @PathVariable Long outbOrderId) {
        outbWaveService.removeOrder(wavId, outbOrderId);
    }

    @DeleteMapping("/{wavId}")
    public void disband(@PathVariable Long wavId) {
        outbWaveService.disband(wavId);
    }
}
