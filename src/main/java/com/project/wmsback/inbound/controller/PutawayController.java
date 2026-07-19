package com.project.wmsback.inbound.controller;

import com.project.wmsback.inbound.dto.PutawayCandidateResponse;
import com.project.wmsback.inbound.dto.PutawayRequest;
import com.project.wmsback.inbound.dto.PutawaySearchCond;
import com.project.wmsback.inbound.service.PutawayService;
import com.project.wmsback.master.dto.LocResponse;
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

    @GetMapping("/lines")
    public List<PutawayCandidateResponse> pendingLines(@ModelAttribute PutawaySearchCond cond) {
        return putawayService.pendingLines(cond);
    }

    @GetMapping("/lines/{ibLineId}/candidate-locs")
    public List<LocResponse> candidateLocs(@PathVariable Long ibLineId) {
        return putawayService.candidateLocs(ibLineId);
    }

    @PostMapping("/lines/{ibLineId}")
    public void putaway(@PathVariable Long ibLineId, @RequestBody PutawayRequest req) {
        putawayService.putaway(ibLineId, req.getLotId(), req.getQty(), req.getTargetLocId());
    }
}