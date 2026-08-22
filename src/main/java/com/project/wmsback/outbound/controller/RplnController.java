package com.project.wmsback.outbound.controller;

import com.project.wmsback.outbound.dto.RplnActionResponse;
import com.project.wmsback.outbound.dto.RplnRowResponse;
import com.project.wmsback.outbound.dto.RplnSearchCond;
import com.project.wmsback.outbound.dto.RplnTaskRequest;
import com.project.wmsback.outbound.dto.RplnWaveResponse;
import com.project.wmsback.outbound.service.RplnService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 수시보충 API. 보충지시는 피킹지시 발행이 만들고(등록 API 없음), 여기서는 확정·취소만 한다.
 * 웨이브 상세가 {@code /outbound/waves/{wavId}/…} 아래 있는 것은 피킹지시·피킹 API와 같은 결이다.
 */
@RestController
@RequiredArgsConstructor
public class RplnController {

    private final RplnService rplnService;

    /** 보충지시가 있는 ISSUED 웨이브 — 미확정 건수가 0이 아니면 화면이 강조한다 */
    @GetMapping("/outbound/replenishment/waves")
    public List<RplnWaveResponse> waves(@ModelAttribute RplnSearchCond cond) {
        return rplnService.searchWaves(cond);
    }

    /** 웨이브의 보충지시 — 피킹 순번 순 */
    @GetMapping("/outbound/waves/{wavId}/replenishments")
    public List<RplnRowResponse> rows(@PathVariable Long wavId) {
        return rplnService.rows(wavId);
    }

    /** 보충 확정 — 전량. 보관존 → 피킹존 실물 이동, 예약 동행, 할당 재지정. 한 트랜잭션 */
    @PostMapping("/outbound/replenishment/confirm")
    public RplnActionResponse confirm(@RequestBody RplnTaskRequest request) {
        return rplnService.confirm(request);
    }

    /** 보충 취소 — 예약 변화 없음. 짝 피킹지시는 실행 가드에 막히므로 다시 내려면 지시취소 → 추가 발행 */
    @PostMapping("/outbound/replenishment/cancel")
    public RplnActionResponse cancel(@RequestBody RplnTaskRequest request) {
        return rplnService.cancel(request);
    }
}
