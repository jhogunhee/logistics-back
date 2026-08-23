package com.project.wmsback.inventory.controller;

import com.project.wmsback.inventory.dto.SpmtIssueRequest;
import com.project.wmsback.inventory.dto.SpmtTargetResponse;
import com.project.wmsback.inventory.dto.SpmtTargetSearchCond;
import com.project.wmsback.inventory.service.SpmtService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 정기 보충 — 대상 산정(미리보기)과 지시 발행.
 * 발행된 지시의 목록·확정·취소는 이동지시 리소스(/inventory/moves)가 그대로 맡는다.
 */
@RestController
@RequestMapping("/inventory/spmt")
@RequiredArgsConstructor
public class SpmtController {

    private final SpmtService spmtService;

    /** 보충 대상 조회 — min 미달 고정로케이션 + FEFO 추천 배정. 저장하지 않는다 */
    @GetMapping("/targets")
    public List<SpmtTargetResponse> targets(@ModelAttribute SpmtTargetSearchCond cond) {
        return spmtService.plan(cond);
    }

    /** 보충지시 일괄 발행 (SPMT 이동지시 생성 + 원천 재고 예약). 한 건이라도 실패하면 전량 롤백 */
    @PostMapping
    public List<String> issue(@RequestBody SpmtIssueRequest request) {
        return spmtService.issue(request);
    }
}
