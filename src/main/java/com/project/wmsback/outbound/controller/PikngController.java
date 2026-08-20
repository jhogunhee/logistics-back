package com.project.wmsback.outbound.controller;

import com.project.wmsback.outbound.dto.PickingSearchCond;
import com.project.wmsback.outbound.dto.PickingWaveResponse;
import com.project.wmsback.outbound.dto.PikngExecuteRequest;
import com.project.wmsback.outbound.dto.PikngExecuteResponse;
import com.project.wmsback.outbound.service.PikngService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 피킹(실행) API. 발행된 지시에 실적 수량을 입력하면 재고가 보관 → SHIP-STAGE로 실제 이동한다
 * (tx PICK). 지시 행 목록은 피킹지시와 같은 웨이브 상세({@code /outbound/waves/{wavId}/picking-tasks})를 쓴다.
 */
@RestController
@RequiredArgsConstructor
public class PikngController {

    private final PikngService pikngService;

    /** 피킹 화면 웨이브 목록 — ISSUED 웨이브의 지시/피킹/잔량 집계 */
    @GetMapping("/outbound/picking/waves")
    public List<PickingWaveResponse> waves(@ModelAttribute PickingSearchCond cond) {
        return pikngService.searchWaves(cond);
    }

    /** 피킹 실행 — 행마다 부분 수량 허용, 요청은 한 트랜잭션(한 건이라도 걸리면 전량 롤백) */
    @PostMapping("/outbound/picking/execute")
    public PikngExecuteResponse execute(@RequestBody PikngExecuteRequest request) {
        return pikngService.execute(request);
    }
}
