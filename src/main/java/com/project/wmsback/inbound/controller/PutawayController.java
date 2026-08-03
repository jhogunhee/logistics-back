package com.project.wmsback.inbound.controller;

import com.project.wmsback.inbound.dto.PutawayCandidateResponse;
import com.project.wmsback.inbound.dto.PutawayRequest;
import com.project.wmsback.inbound.dto.PutawaySearchCond;
import com.project.wmsback.inbound.service.PutawayService;
import com.project.wmsback.warehouse.dto.LocResponse;
import com.project.wmsback.strategy.putaway.dto.PutawayRecommendRequest;
import com.project.wmsback.strategy.putaway.dto.PutawayRecommendResponse;
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

@RestController
@RequestMapping("/inbound/putaway")
@RequiredArgsConstructor
public class PutawayController {

    private final PutawayService putawayService;
    private final PutawayRecommendService putawayRecommendService;

    @GetMapping("/lines")
    public List<PutawayCandidateResponse> pendingLines(@ModelAttribute PutawaySearchCond cond) {
        return putawayService.pendingLines(cond);
    }

    @GetMapping("/lines/{ibLineId}/candidate-locs")
    public List<LocResponse> candidateLocs(@PathVariable Long ibLineId) {
        return putawayService.candidateLocs(ibLineId);
    }

    /**
     * 적치 전략 추천 — (로케이션, 수량) N행과 단계별 근거를 반환한다. 전략 미설정이면
     * strategySelected=false — 화면은 기존 수동 후보(candidate-locs)로 폴백한다.
     * 실행은 기존 POST /lines/{ibLineId} 그대로다 (추천은 예약이 아니다).
     */
    @PostMapping("/lines/{ibLineId}/recommend")
    public PutawayRecommendResponse recommend(@PathVariable Long ibLineId,
                                              @RequestBody PutawayRecommendRequest req) {
        return putawayRecommendService.recommend(ibLineId, req);
    }

    @PostMapping("/lines/{ibLineId}")
    public void putaway(@PathVariable Long ibLineId, @RequestBody PutawayRequest req) {
        putawayService.putaway(ibLineId, req.getLotId(), req.getQty(), req.getTargetLocId());
    }
}