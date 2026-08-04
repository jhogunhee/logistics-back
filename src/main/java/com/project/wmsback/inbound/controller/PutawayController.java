package com.project.wmsback.inbound.controller;

import com.project.wmsback.inbound.dto.PutawayBulkExecuteRequest;
import com.project.wmsback.inbound.dto.PutawayCandidateResponse;
import com.project.wmsback.inbound.dto.PutawayExecuteRequest;
import com.project.wmsback.inbound.dto.PutawayLocCandidateResponse;
import com.project.wmsback.inbound.dto.PutawaySearchCond;
import com.project.wmsback.inbound.dto.PutawayTaskCreateRequest;
import com.project.wmsback.inbound.dto.PutawayTaskResponse;
import com.project.wmsback.inbound.dto.PutawayTaskSearchCond;
import com.project.wmsback.inbound.service.PutawayService;
import com.project.wmsback.inbound.service.PutawayTaskService;
import com.project.wmsback.strategy.putaway.dto.PutawayBulkRecommendRequest;
import com.project.wmsback.strategy.putaway.dto.PutawayBulkRecommendResponse;
import com.project.wmsback.strategy.putaway.service.PutawayRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 적치 — 지시 발행(적치지시 화면)과 지시 실행(적치 화면)의 두 경로.
 * 지시 없이 로케이션을 직접 골라 즉시 옮기던 예전 경로는 없앴다 (docs/design.md 「적치 지시」).
 */
@RestController
@RequestMapping("/inbound/putaway")
@RequiredArgsConstructor
public class PutawayController {

    private final PutawayService putawayService;
    private final PutawayTaskService putawayTaskService;
    private final PutawayRecommendService putawayRecommendService;

    /** 적치 대기 배치 (입고라인, Lot) — 미지시 수량 포함 */
    @GetMapping("/lines")
    public List<PutawayCandidateResponse> pendingLines(@ModelAttribute PutawaySearchCond cond) {
        return putawayTaskService.candidates(cond);
    }

    /** 수동 지시용 로케이션 후보 (적재가능수량 포함) */
    @GetMapping("/lines/{ibLineId}/candidate-locs")
    public List<PutawayLocCandidateResponse> candidateLocs(@PathVariable Long ibLineId) {
        return putawayService.candidateLocs(ibLineId);
    }

    /**
     * 적치지시 일괄 추천 — 배치별로 (로케이션, 수량)을 배정해 돌려준다. 저장하지 않으므로
     * 화면이 확인한 뒤 POST /tasks로 생성한다. 전략 미설정 배치는 strategySelected=false로 온다.
     */
    @PostMapping("/tasks/preview")
    public PutawayBulkRecommendResponse previewTasks(@RequestBody PutawayBulkRecommendRequest req) {
        return putawayRecommendService.recommendBulk(req);
    }

    /** 적치지시 생성 (추천 결과 또는 수동 배정). 한 건이라도 실패하면 전량 롤백 */
    @PostMapping("/tasks")
    public List<Long> createTasks(@RequestBody PutawayTaskCreateRequest req) {
        return putawayTaskService.create(req);
    }

    @GetMapping("/tasks")
    public List<PutawayTaskResponse> tasks(@ModelAttribute PutawayTaskSearchCond cond) {
        return putawayTaskService.list(cond);
    }

    /** 적치 실행 (부분 허용) — 지시받은 로케이션으로만 */
    @PostMapping("/tasks/{taskId}/execute")
    public void execute(@PathVariable Long taskId, @RequestBody PutawayExecuteRequest req) {
        putawayService.execute(taskId, req.getQty());
    }

    /** 적치 일괄 실행 — 한 상품의 지시 여러 건을 한 번에. 한 건이라도 실패하면 전량 롤백 */
    @PostMapping("/tasks/execute")
    public void executeAll(@RequestBody PutawayBulkExecuteRequest req) {
        putawayService.executeAll(req);
    }

    /** 적치지시 취소 (예약 해제). 실행 실적이 없는 지시만 */
    @PostMapping("/tasks/{taskId}/cancel")
    public void cancel(@PathVariable Long taskId) {
        putawayTaskService.cancel(taskId);
    }
}
