package com.project.wmsback.inventory.controller;

import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.wmsback.inventory.dto.InvAdjHldTargetResponse;
import com.project.wmsback.inventory.dto.InvAdjRequest;
import com.project.wmsback.inventory.dto.InvAdjResponse;
import com.project.wmsback.inventory.dto.InvAdjSearchCond;
import com.project.wmsback.inventory.dto.InvAdjTargetResponse;
import com.project.wmsback.inventory.dto.InvAdjTargetSearchCond;
import com.project.wmsback.inventory.service.InvAdjService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 재고조정 — 장부와 실물을 함께 증감시키는 의도된 처분(폐기·견본출고).
 * 장부와 실물의 차이를 정정하는 재고조사와는 별개 경로다.
 */
@RestController
@RequestMapping("/inventory/adjs")
@RequiredArgsConstructor
public class InvAdjController {

    private final InvAdjService invAdjService;

    /** 가용 라인 대상 조회 — 보관 로케이션 재고 행 (가용 0인 행도 포함, (+) 조정 대상이다) */
    @GetMapping("/targets")
    public List<InvAdjTargetResponse> listTargets(@ModelAttribute InvAdjTargetSearchCond cond) {
        return invAdjService.listTargets(cond);
    }

    /** 보류 라인 대상 조회 — 미해제 잔량이 남은 보류 건 (불량 반품 폐기의 진입점) */
    @GetMapping("/hld-targets")
    public List<InvAdjHldTargetResponse> listHldTargets(@ModelAttribute InvAdjTargetSearchCond cond) {
        return invAdjService.listHldTargets(cond);
    }

    /** 조정 실행. 발급된 재고조정 번호 목록을 돌려준다 (요청 순서) */
    @PostMapping
    public List<String> adjust(@RequestBody InvAdjRequest request) {
        return invAdjService.adjust(request);
    }

    /** 실적 조회 (append-only 로그 — 서버 페이징. 조건과 페이지를 따로 받는다) */
    @GetMapping
    public PageResponse<InvAdjResponse> list(@ModelAttribute InvAdjSearchCond cond, @ModelAttribute PageCond pageCond) {
        return invAdjService.list(cond, pageCond);
    }
}
