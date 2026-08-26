package com.project.wmsback.inventory.controller;

import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.wmsback.inventory.dto.InvHldAcrstResponse;
import com.project.wmsback.inventory.dto.InvHldAcrstSearchCond;
import com.project.wmsback.inventory.dto.InvHldRegisterRequest;
import com.project.wmsback.inventory.dto.InvHldReleaseRequest;
import com.project.wmsback.inventory.dto.InvHldResponse;
import com.project.wmsback.inventory.dto.InvHldSearchCond;
import com.project.wmsback.inventory.service.InvHldService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/inventory/holds")
@RequiredArgsConstructor
public class InvHldController {

    private final InvHldService invHldService;

    /** 보류 등록 (등록 즉시 발효). 발급된 보류 번호 목록을 돌려준다 */
    @PostMapping
    public List<String> register(@RequestBody InvHldRegisterRequest request) {
        return invHldService.register(request);
    }

    @GetMapping
    public List<InvHldResponse> list(@ModelAttribute InvHldSearchCond cond) {
        return invHldService.list(cond);
    }

    /** 보류 해제 (건을 지목해 다건, 건마다 부분 해제 허용) */
    @PostMapping("/release")
    public void release(@RequestBody InvHldReleaseRequest request) {
        invHldService.release(request);
    }

    /** 보류 실적 조회 (등록 로그) */
    @GetMapping("/acrsts")
    public PageResponse<InvHldAcrstResponse> listAcrst(@ModelAttribute InvHldAcrstSearchCond cond,
                                                       @ModelAttribute PageCond pageCond) {
        return invHldService.listAcrst(cond, pageCond);
    }

    /** 해제 실적 조회 */
    @GetMapping("/rlz-acrsts")
    public PageResponse<InvHldAcrstResponse> listRlzAcrst(@ModelAttribute InvHldAcrstSearchCond cond,
                                                          @ModelAttribute PageCond pageCond) {
        return invHldService.listRlzAcrst(cond, pageCond);
    }
}
