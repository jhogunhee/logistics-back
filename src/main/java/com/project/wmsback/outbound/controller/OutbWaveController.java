package com.project.wmsback.outbound.controller;

import com.project.wmsback.outbound.dto.OutbWaveOrdersRequest;
import com.project.wmsback.outbound.dto.OutbWaveResponse;
import com.project.wmsback.outbound.dto.OutbWaveSearchCond;
import com.project.wmsback.outbound.service.OutbWaveService;
import com.project.wmsback.strategy.wave.dto.WaveStgyExecRequest;
import com.project.wmsback.strategy.wave.dto.WaveStgyExecResponse;
import com.project.wmsback.strategy.wave.service.WaveStgyExecService;
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
    private final WaveStgyExecService waveStgyExecService;

    @GetMapping
    public List<OutbWaveResponse> list(@ModelAttribute OutbWaveSearchCond cond) {
        return outbWaveService.list(cond);
    }

    /**
     * 웨이브 전략 실행 — 조건에 맞는 미편성 주문을 전략별 웨이브로 편성한다.
     * body의 wavStgyId를 주면 선택실행, 비우면 전 전략 자동실행.
     * 편입 0건인 전략은 웨이브를 만들지 않으므로 재실행해도 빈 웨이브가 쌓이지 않는다.
     */
    @PostMapping("/stgy-exec")
    public WaveStgyExecResponse stgyExec(@RequestBody WaveStgyExecRequest req) {
        return waveStgyExecService.execute(req);
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
