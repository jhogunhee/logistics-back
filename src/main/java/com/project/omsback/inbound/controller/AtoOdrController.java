package com.project.omsback.inbound.controller;

import com.project.common.batch.BatchResult;
import com.project.omsback.inbound.dto.AtoOdrIssueRequest;
import com.project.omsback.inbound.dto.AtoOdrProposalResponse;
import com.project.omsback.inbound.dto.AtoOdrSearchCond;
import com.project.omsback.inbound.service.AtoOdrService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/oms/ato-odr")
@RequiredArgsConstructor
public class AtoOdrController {

    private final AtoOdrService atoOdrService;

    /** 발주 제안 산정. 저장되는 것은 없다 — 조회할 때마다 그 시점의 재고로 다시 센다 */
    @GetMapping("/plan")
    public List<AtoOdrProposalResponse> plan(@ModelAttribute AtoOdrSearchCond cond) {
        return atoOdrService.plan(cond);
    }

    /** 발행 — 벤더 1곳이 입고주문 1건. 트랜잭션이 벤더 단위라 결과가 성공·실패로 갈려 온다 */
    @PostMapping
    public BatchResult issue(@RequestBody List<AtoOdrIssueRequest> requests) {
        return atoOdrService.issueAll(requests);
    }
}
