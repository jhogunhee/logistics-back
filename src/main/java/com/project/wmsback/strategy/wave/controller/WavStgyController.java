package com.project.wmsback.strategy.wave.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.wmsback.strategy.core.dto.RvsnResponse;
import com.project.wmsback.strategy.wave.dto.WavPreviewRequest;
import com.project.wmsback.strategy.wave.dto.WavPreviewResponse;
import com.project.wmsback.strategy.wave.dto.WavStgyDefinition;
import com.project.wmsback.strategy.wave.dto.WavStgyResponse;
import com.project.wmsback.strategy.wave.dto.WavStgySummaryResponse;
import com.project.wmsback.strategy.wave.service.WavStgyService;
import com.project.wmsback.strategy.wave.service.WaveStgyExecService;
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

/**
 * 웨이브 전략관리 (SC-03). 정의의 CRUD와 미리보기까지가 여기 몫이고,
 * 실제 편성 실행은 업무 도메인(POST /outbound/waves/stgy-exec)에 있다 —
 * 전략은 업무 서비스가 주입받아 쓰는 정책이다.
 */
@RestController
@RequestMapping("/strategy/wave-strategies")
@RequiredArgsConstructor
public class WavStgyController {

    private final WavStgyService wavStgyService;
    private final WaveStgyExecService waveStgyExecService;

    @GetMapping
    public List<WavStgySummaryResponse> list() {
        return wavStgyService.list();
    }

    @PostMapping
    public WavStgyResponse create(@RequestBody WavStgyDefinition definition) {
        return wavStgyService.create(definition);
    }

    @GetMapping("/{id}")
    public WavStgyResponse get(@PathVariable Long id) {
        return wavStgyService.get(id);
    }

    @PutMapping("/{id}")
    public WavStgyResponse update(@PathVariable Long id, @RequestBody WavStgyDefinition definition) {
        return wavStgyService.update(id, definition);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        wavStgyService.delete(id);
    }

    /** 미저장 정의 미리보기 — 편집 중 실시간 확인용 (P4). 검증 후 판정하므로 잘못된 정의는 400 */
    @PostMapping("/preview")
    public WavPreviewResponse preview(@RequestBody WavPreviewRequest request) {
        if (request.definition() == null) {
            throw new IllegalArgumentException("미리보기할 정의가 없습니다.");
        }
        WavStgyDefinition normalized = wavStgyService.validate(request.definition());
        return waveStgyExecService.preview(normalized, request);
    }

    /** 저장본 미리보기 */
    @PostMapping("/{id}/preview")
    public WavPreviewResponse previewSaved(@PathVariable Long id, @RequestBody WavPreviewRequest request) {
        WavStgyDefinition definition = wavStgyService.get(id).toDefinition();
        return waveStgyExecService.preview(definition, request);
    }

    @GetMapping("/{id}/revisions")
    public List<RvsnResponse> revisions(@PathVariable Long id) {
        return wavStgyService.revisions(id);
    }

    @GetMapping("/{id}/revisions/{rvsnNo}")
    public JsonNode revision(@PathVariable Long id, @PathVariable Long rvsnNo) {
        return wavStgyService.revision(id, rvsnNo);
    }
}
