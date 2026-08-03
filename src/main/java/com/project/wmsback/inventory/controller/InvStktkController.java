package com.project.wmsback.inventory.controller;

import com.project.wmsback.inventory.dto.InvStktkCreateRequest;
import com.project.wmsback.inventory.dto.InvStktkDetailResponse;
import com.project.wmsback.inventory.dto.InvStktkLnAddRequest;
import com.project.wmsback.inventory.dto.InvStktkLnSaveRequest;
import com.project.wmsback.inventory.dto.InvStktkResponse;
import com.project.wmsback.inventory.dto.InvStktkSearchCond;
import com.project.wmsback.inventory.service.InvStktkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/inventory/stocktakes")
@RequiredArgsConstructor
public class InvStktkController {

    private final InvStktkService invStktkService;

    /** 조사 생성 (범위 지정 → 라인 자동 생성 + 전산수량 스냅샷). 발급된 조사번호를 돌려준다 */
    @PostMapping
    public String create(@RequestBody InvStktkCreateRequest request) {
        return invStktkService.create(request);
    }

    @GetMapping
    public List<InvStktkResponse> list(@ModelAttribute InvStktkSearchCond cond) {
        return invStktkService.list(cond);
    }

    /** 조사 상세 (헤더 + 라인. 라인에는 현재 전산수량·예약·보류가 함께 실린다) */
    @GetMapping("/{id}")
    public InvStktkDetailResponse detail(@PathVariable Long id) {
        return invStktkService.detail(id);
    }

    /** 실사수량·사유 저장 (작성 중) */
    @PutMapping("/{id}/lines")
    public void saveLines(@PathVariable Long id, @RequestBody InvStktkLnSaveRequest request) {
        invStktkService.saveLines(id, request);
    }

    /** 라인 수동 추가 (장부에 없는 재고 · 기초재고 등록) */
    @PostMapping("/{id}/lines")
    public void addLine(@PathVariable Long id, @RequestBody InvStktkLnAddRequest request) {
        invStktkService.addLine(id, request);
    }

    @DeleteMapping("/{id}/lines/{lnId}")
    public void deleteLine(@PathVariable Long id, @PathVariable Long lnId) {
        invStktkService.deleteLine(id, lnId);
    }

    /** 전산수량 재스냅샷 (조사 중 재고가 변했을 때 화면 기준값 갱신 — 실사수량은 유지) */
    @PostMapping("/{id}/resync")
    public void resync(@PathVariable Long id) {
        invStktkService.resync(id);
    }

    /** 확정 (차이만큼 ADJUST 기록 + 재고 보정) */
    @PostMapping("/{id}/confirm")
    public void confirm(@PathVariable Long id) {
        invStktkService.confirm(id);
    }

    /** 조사 취소 (확정 전 폐기) */
    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable Long id) {
        invStktkService.cancel(id);
    }
}
