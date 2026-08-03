package com.project.wmsback.strategy.allocation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.wmsback.strategy.allocation.dto.AlocPreviewRequest;
import com.project.wmsback.strategy.allocation.dto.AlocPreviewResponse;
import com.project.wmsback.strategy.allocation.dto.AlocStgyDefinition;
import com.project.wmsback.strategy.allocation.dto.AlocStgyResponse;
import com.project.wmsback.strategy.allocation.dto.AlocStgySummaryResponse;
import com.project.wmsback.strategy.allocation.service.AllocPreviewService;
import com.project.wmsback.strategy.allocation.service.AlocStgyService;
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
 * 할당 전략관리 (SC-04). 정의의 CRUD와 미리보기까지가 여기 몫이고,
 * 실제 할당 실행은 업무 도메인(POST /outbound/allocations/execute)에 있다 —
 * 전략은 업무 서비스가 주입받아 쓰는 정책이다.
 *
 * <p>웨이브 전략과 달리 <b>실행 엔드포인트를 두지 않는다.</b> 할당 실행 진입점은 이미
 * 재고할당 화면에 있고, 전략 화면에 두 번째 진입점을 만들면 같은 업무가 두 곳에서 시작된다.
 */
@RestController
@RequestMapping("/strategy/allocation-strategies")
@RequiredArgsConstructor
public class AlocStgyController {

    private final AlocStgyService alocStgyService;
    private final AllocPreviewService allocPreviewService;

    @GetMapping
    public List<AlocStgySummaryResponse> list() {
        return alocStgyService.list();
    }

    @PostMapping
    public AlocStgyResponse create(@RequestBody AlocStgyDefinition definition) {
        return alocStgyService.create(definition);
    }

    @GetMapping("/{id}")
    public AlocStgyResponse get(@PathVariable Long id) {
        return alocStgyService.get(id);
    }

    @PutMapping("/{id}")
    public AlocStgyResponse update(@PathVariable Long id, @RequestBody AlocStgyDefinition definition) {
        return alocStgyService.update(id, definition);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        alocStgyService.delete(id);
    }

    /** 미저장 정의 미리보기 — 편집 중 실시간 확인용 (P4). 검증 후 산정하므로 잘못된 정의는 400 */
    @PostMapping("/preview")
    public AlocPreviewResponse preview(@RequestBody AlocPreviewRequest request) {
        if (request.definition() == null) {
            throw new IllegalArgumentException("미리보기할 정의가 없습니다.");
        }
        AlocStgyDefinition normalized = alocStgyService.validate(request.definition());
        return allocPreviewService.preview(normalized, null, null, request);
    }

    /** 저장본 미리보기 */
    @PostMapping("/{id}/preview")
    public AlocPreviewResponse previewSaved(@PathVariable Long id, @RequestBody AlocPreviewRequest request) {
        AlocStgyResponse saved = alocStgyService.get(id);
        return allocPreviewService.preview(saved.toDefinition(), id, saved.lastRvsnNo(), request);
    }

    @GetMapping("/{id}/revisions")
    public List<com.project.wmsback.strategy.core.dto.RvsnResponse> revisions(@PathVariable Long id) {
        return alocStgyService.revisions(id);
    }

    @GetMapping("/{id}/revisions/{rvsnNo}")
    public JsonNode revision(@PathVariable Long id, @PathVariable Long rvsnNo) {
        return alocStgyService.revision(id, rvsnNo);
    }
}
