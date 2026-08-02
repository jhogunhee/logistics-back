package com.project.wmsback.strategy.inspection.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.wmsback.strategy.core.dto.RvsnResponse;
import com.project.wmsback.strategy.inspection.dto.InspPlcyDefinition;
import com.project.wmsback.strategy.inspection.dto.InspPlcyResponse;
import com.project.wmsback.strategy.inspection.dto.InspPreviewRequest;
import com.project.wmsback.strategy.inspection.dto.InspPreviewResponse;
import com.project.wmsback.strategy.inspection.service.InspPlcyService;
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

/** 검수 정책관리 (SC-01). 정책은 전역 1개라 경로에 id가 없다 */
@RestController
@RequestMapping("/strategy/inspection-policy")
@RequiredArgsConstructor
public class InspPlcyController {

    private final InspPlcyService inspPlcyService;

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

    @GetMapping("/revisions")
    public List<RvsnResponse> revisions() {
        return inspPlcyService.revisions();
    }

    @GetMapping("/revisions/{rvsnNo}")
    public JsonNode revision(@PathVariable Long rvsnNo) {
        return inspPlcyService.revision(rvsnNo);
    }

    @PostMapping("/revisions/{rvsnNo}/restore")
    public InspPlcyResponse restore(@PathVariable Long rvsnNo) {
        return inspPlcyService.restore(rvsnNo);
    }
}
