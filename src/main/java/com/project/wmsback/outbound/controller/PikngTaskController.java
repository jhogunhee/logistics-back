package com.project.wmsback.outbound.controller;

import com.project.wmsback.outbound.dto.PikngAcrstResponse;
import com.project.wmsback.outbound.dto.PikngCancelRequest;
import com.project.wmsback.outbound.dto.PikngCancelResponse;
import com.project.wmsback.outbound.dto.PikngIssueRequest;
import com.project.wmsback.outbound.dto.PikngIssueResponse;
import com.project.wmsback.outbound.dto.PikngTaskSearchCond;
import com.project.wmsback.outbound.dto.PikngWaveDetailResponse;
import com.project.wmsback.outbound.dto.PikngWaveResponse;
import com.project.wmsback.outbound.service.PikngTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 피킹지시 API. <b>발행 단위는 웨이브</b>이고 지시 행은 할당과 1:1이다.
 *
 * <p>웨이브 상세가 {@code /outbound/waves/{wavId}/…} 아래 있는 것은 할당 API와 같은 결이다.
 * 발행·취소는 웨이브 하나에 매이지 않아 {@code /outbound/picking-tasks} 아래 둔다.
 */
@RestController
@RequiredArgsConstructor
public class PikngTaskController {

    private final PikngTaskService pikngTaskService;

    /** 피킹지시 화면 웨이브 목록 — 할당 있는 PLANNED(발행 대상) + ISSUED(확인·취소 대상) */
    @GetMapping("/outbound/picking-tasks/waves")
    public List<PikngWaveResponse> waves(@ModelAttribute PikngTaskSearchCond cond) {
        return pikngTaskService.searchWaves(cond);
    }

    /** 웨이브 상세 — 발행 전: 할당 행(발행 미리보기) / 발행 후: 지시 행(스냅샷) + 할당 0건 주문 */
    @GetMapping("/outbound/waves/{wavId}/picking-tasks")
    public PikngWaveDetailResponse detail(@PathVariable Long wavId) {
        return pikngTaskService.detail(wavId);
    }

    /** 피킹지시 발행 — 할당 0건 주문이 섞인 웨이브는 거부된다. 여러 웨이브를 보내도 한 트랜잭션이다 */
    @PostMapping("/outbound/picking-tasks/issue")
    public PikngIssueResponse issue(@RequestBody PikngIssueRequest request) {
        return pikngTaskService.issue(request);
    }

    /** 지시취소 — 웨이브 단위, 실적 0일 때만. 웨이브는 PLANNED로 복귀한다 */
    @PostMapping("/outbound/picking-tasks/cancel")
    public PikngCancelResponse cancel(@RequestBody PikngCancelRequest request) {
        return pikngTaskService.cancel(request);
    }

    /** 지시의 실행 실적 로그 — 실적 내역 모달 */
    @GetMapping("/outbound/picking-tasks/{taskId}/acrsts")
    public List<PikngAcrstResponse> acrsts(@PathVariable Long taskId) {
        return pikngTaskService.acrsts(taskId);
    }
}
