package com.project.wmsback.strategy.inspection.controller;

import com.project.wmsback.strategy.inspection.dto.InspMinMfgDtRequest;
import com.project.wmsback.strategy.inspection.dto.InspMinMfgDtResponse;
import com.project.wmsback.strategy.inspection.dto.InspPlcyDefinition;
import com.project.wmsback.strategy.inspection.dto.InspPlcyResponse;
import com.project.wmsback.strategy.inspection.dto.InspPreviewRequest;
import com.project.wmsback.strategy.inspection.dto.InspPreviewResponse;
import com.project.wmsback.strategy.inspection.service.InspPlcyService;
import com.project.wmsback.strategy.inspection.service.InspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 검수 정책관리 (SC-01). 정책은 전역 1개라 경로에 id가 없다 */
@RestController
@RequestMapping("/strategy/inspection-policy")
@RequiredArgsConstructor
public class InspPlcyController {

    private final InspPlcyService inspPlcyService;
    private final InspectionService inspectionService;

    @GetMapping
    public InspPlcyResponse get() {
        return inspPlcyService.get();
    }

    @PostMapping
    public InspPlcyResponse create(@RequestBody InspPlcyDefinition definition) {
        return inspPlcyService.create(definition);
    }

    @PutMapping
    public InspPlcyResponse update(@RequestBody InspPlcyDefinition definition) {
        return inspPlcyService.update(definition);
    }

    @DeleteMapping
    public void delete() {
        inspPlcyService.delete();
    }

    @PostMapping("/preview")
    public InspPreviewResponse preview(@RequestBody InspPreviewRequest request) {
        return inspPlcyService.preview(request);
    }

    /**
     * 검수 입력 전 힌트 — 저장본 정책 기준으로 상품·입고일자마다 입고 가능한 가장 이른 제조일자.
     * 조회인데 POST인 이유는 상품·입고일자 짝을 목록으로 보내기 때문(라인마다 부르면 원격 DB 왕복이 곧 지연)
     */
    @PostMapping("/min-mfg-dt")
    public InspMinMfgDtResponse minMfgDts(@RequestBody InspMinMfgDtRequest request) {
        return inspectionService.minMfgDts(request);
    }
}
