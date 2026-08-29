package com.project.wmsback.outbound.controller;

import com.project.wmsback.outbound.dto.AllocCandidateResponse;
import com.project.wmsback.outbound.dto.AllocExecuteRequest;
import com.project.wmsback.outbound.dto.AllocExecuteResponse;
import com.project.wmsback.outbound.dto.AllocReleaseRequest;
import com.project.wmsback.outbound.dto.AllocTargetSearchCond;
import com.project.wmsback.outbound.dto.AllocWaveDetailResponse;
import com.project.wmsback.outbound.dto.AllocWaveResponse;
import com.project.wmsback.outbound.dto.ManualAllocRequest;
import com.project.wmsback.outbound.service.OutbAllocService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 재고 할당 API. <b>실행 단위는 웨이브</b>이고 결과는 라인 단위로 남는다.
 *
 * <p>웨이브 상세·수동할당이 {@code /outbound/waves/{wavId}/…} 아래 있는 것은
 * {@code OutbWaveController}의 {@code /{wavId}/orders} 와 같은 결을 유지하려는 것이다.
 * 반대로 대상 목록과 해제는 웨이브 하나에 매이지 않아 {@code /outbound/allocations} 아래 둔다.
 */
@RestController
@RequiredArgsConstructor
public class OutbAllocController {

    private final OutbAllocService outbAllocService;

    /** 할당 대상 웨이브 목록 — 잔량이 남은 PLANNED 웨이브 */
    @GetMapping("/outbound/allocations/waves")
    public List<AllocWaveResponse> targetWaves(@ModelAttribute AllocTargetSearchCond cond) {
        return outbAllocService.searchTargetWaves(cond);
    }

    /** 웨이브의 라인별 주문/할당/잔량 + 할당 레코드 */
    @GetMapping("/outbound/waves/{wavId}/allocations")
    public AllocWaveDetailResponse detail(@PathVariable Long wavId) {
        return outbAllocService.detail(wavId);
    }

    /** 자동할당 (FEFO). 여러 웨이브를 함께 보내도 한 트랜잭션이다 */
    @PostMapping("/outbound/allocations/execute")
    public AllocExecuteResponse execute(@RequestBody AllocExecuteRequest request) {
        return outbAllocService.execute(request);
    }

    /** 수동할당 후보 재고 — 잔여수명 미달도 함께 내리고 경고는 화면이 한다 */
    @GetMapping("/outbound/allocations/candidates")
    public List<AllocCandidateResponse> candidates(@RequestParam Long outbLineId) {
        return outbAllocService.candidates(outbLineId);
    }

    /** 수동할당 — 라인 ↔ 재고 직접 지정. 2026-08-29 웨이브 접두에서 이 이름공간으로 옮겼다 */
    @PostMapping("/outbound/allocations/manual")
    public AllocExecuteResponse allocateManual(@RequestBody ManualAllocRequest request) {
        return outbAllocService.allocateManual(request);
    }

    /** 할당해제 — 피킹이 시작된 행은 거부된다 */
    @PostMapping("/outbound/allocations/release")
    public ResponseEntity<Void> release(@RequestBody AllocReleaseRequest request) {
        outbAllocService.release(request);
        return ResponseEntity.noContent().build();
    }
}
