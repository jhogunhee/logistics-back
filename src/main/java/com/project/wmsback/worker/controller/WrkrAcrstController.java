package com.project.wmsback.worker.controller;

import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.wmsback.worker.dto.WrkrAcrstDailyResponse;
import com.project.wmsback.worker.dto.WrkrAcrstDetailResponse;
import com.project.wmsback.worker.dto.WrkrAcrstSearchCond;
import com.project.wmsback.worker.dto.WrkrAcrstSummaryResponse;
import com.project.wmsback.worker.dto.WrkrOptionResponse;
import com.project.wmsback.worker.service.WrkrAcrstService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 작업자 실적 조회. 접두가 {@code /wrkr}인 이유는 실적 열람이 재고 업무가 아니라 센터 운영이기
 * 때문이다 — {@code SecurityConfig}가 이 접두를 관리자·센터관리자로 잠근다.
 */
@RestController
@RequestMapping("/wrkr/acrst")
@RequiredArgsConstructor
public class WrkrAcrstController {

    private final WrkrAcrstService wrkrAcrstService;

    @GetMapping("/summary")
    public List<WrkrAcrstSummaryResponse> summary(@ModelAttribute WrkrAcrstSearchCond cond) {
        return wrkrAcrstService.summary(cond);
    }

    @GetMapping("/daily")
    public List<WrkrAcrstDailyResponse> daily(@ModelAttribute WrkrAcrstSearchCond cond) {
        return wrkrAcrstService.daily(cond);
    }

    @GetMapping("/workers")
    public List<WrkrOptionResponse> workers(@ModelAttribute WrkrAcrstSearchCond cond) {
        return wrkrAcrstService.workers(cond);
    }

    @GetMapping("/detail")
    public PageResponse<WrkrAcrstDetailResponse> detail(@ModelAttribute WrkrAcrstSearchCond cond,
                                                        @ModelAttribute PageCond pageCond) {
        return wrkrAcrstService.detail(cond, pageCond);
    }
}
