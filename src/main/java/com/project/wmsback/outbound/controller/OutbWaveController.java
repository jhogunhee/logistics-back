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

    @PostMapping("/{wavId}/orders/assign")
    public void assignOrders(@PathVariable Long wavId, @RequestBody OutbWaveOrdersRequest req) {
        outbWaveService.assignOrders(wavId, req);
    }

    /**
     * 편성 해제(다건). 주문을 지우는 게 아니라 미편성으로 되돌리는 상태 변경이라 DELETE가 아니고,
     * 담기와 대칭으로 목록을 받아 한 트랜잭션에서 처리한다.
     */
    @PostMapping("/{wavId}/orders/unassign")
    public void unassignOrders(@PathVariable Long wavId, @RequestBody OutbWaveOrdersRequest req) {
        outbWaveService.unassignOrders(wavId, req);
    }

    @DeleteMapping("/{wavId}")
    public void remove(@PathVariable Long wavId) {
        outbWaveService.remove(wavId);
    }
}
