package com.project.wmsback.strategy.putaway.controller;

import com.project.wmsback.strategy.putaway.dto.PtawyPreviewRequest;
import com.project.wmsback.strategy.putaway.dto.PtawyStgyDefinition;
import com.project.wmsback.strategy.putaway.dto.PtawyStgyResponse;
import com.project.wmsback.strategy.putaway.dto.PtawyStgySummaryResponse;
import com.project.wmsback.strategy.putaway.dto.PutawayRecommendResponse;
import com.project.wmsback.strategy.putaway.service.PtawyStgyService;
import com.project.wmsback.strategy.putaway.service.PutawayRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 적치 전략관리 (SC-02) */
@RestController
@RequestMapping("/strategy/putaway-strategies")
@RequiredArgsConstructor
public class PtawyStgyController {

    private final PtawyStgyService ptawyStgyService;
    private final PutawayRecommendService putawayRecommendService;

    @GetMapping
    public List<PtawyStgySummaryResponse> list() {
        return ptawyStgyService.list();
    }

    @PostMapping
    public PtawyStgyResponse create(@RequestBody PtawyStgyDefinition definition) {
        return ptawyStgyService.create(definition);
    }

    @GetMapping("/{id}")
    public PtawyStgyResponse get(@PathVariable Long id) {
        return ptawyStgyService.get(id);
    }

    @PutMapping("/{id}")
    public PtawyStgyResponse update(@PathVariable Long id, @RequestBody PtawyStgyDefinition definition) {
        return ptawyStgyService.update(id, definition);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        ptawyStgyService.delete(id);
    }

    /** 미저장 정의 미리보기 — 편집 중 실시간 확인용 (P4). 검증 후 산정하므로 잘못된 정의는 400 */
    @PostMapping("/preview")
    public PutawayRecommendResponse preview(@RequestBody PtawyPreviewRequest request) {
        if (request.definition() == null) {
            throw new IllegalArgumentException("미리보기할 정의가 없습니다.");
        }
        PtawyStgyDefinition normalized = ptawyStgyService.validate(request.definition());
        return putawayRecommendService.preview(normalized, request);
    }

    /** 저장본 미리보기 */
    @PostMapping("/{id}/preview")
    public PutawayRecommendResponse previewSaved(@PathVariable Long id, @RequestBody PtawyPreviewRequest request) {
        PtawyStgyDefinition definition = ptawyStgyService.get(id).toDefinition();
        return putawayRecommendService.preview(definition, request);
    }
}
