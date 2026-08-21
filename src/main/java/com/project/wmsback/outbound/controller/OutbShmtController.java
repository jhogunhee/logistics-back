package com.project.wmsback.outbound.controller;

import com.project.wmsback.outbound.dto.ShmtConfirmRequest;
import com.project.wmsback.outbound.dto.ShmtConfirmResponse;
import com.project.wmsback.outbound.dto.ShmtOrderResponse;
import com.project.wmsback.outbound.dto.ShmtSearchCond;
import com.project.wmsback.outbound.dto.ShmtWaveResponse;
import com.project.wmsback.outbound.service.OutbShmtService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 출고확정 API. 피킹이 끝난 주문을 닫고 SHIP-STAGE의 실물·예약을 함께 소진한다(tx SHIP) —
 * 재고가 창고 밖으로 나가는 유일한 지점이고, 웨이브의 주문이 전부 닫히면 웨이브도 종료(CLOSED)된다.
 */
@RestController
@RequiredArgsConstructor
public class OutbShmtController {

    private final OutbShmtService outbShmtService;

    /** 출고확정 화면 웨이브 목록 — ISSUED 웨이브와 주문 상태별 건수(확정대상 · 작업중 · 확정완료) */
    @GetMapping("/outbound/shipping/waves")
    public List<ShmtWaveResponse> waves(@ModelAttribute ShmtSearchCond cond) {
        return outbShmtService.searchWaves(cond);
    }

    /** 웨이브의 주문 목록 — 상태 · 주문/할당/피킹/결품 수량 · 확정 가능 여부 */
    @GetMapping("/outbound/shipping/waves/{wavId}/orders")
    public List<ShmtOrderResponse> orders(@PathVariable Long wavId) {
        return outbShmtService.orders(wavId);
    }

    /**
     * 출고확정 — 주문 단위, 한 트랜잭션. PICKED(정상)와 CREATED(할당 0건, 전량 미출고)만 통과하고
     * 작업중(ALLOCATED · PICKING) 주문이 섞이면 전부 거부한다.
     */
    @PostMapping("/outbound/shipping/confirm")
    public ShmtConfirmResponse confirm(@RequestBody ShmtConfirmRequest request) {
        return outbShmtService.confirm(request);
    }
}
