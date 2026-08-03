package com.project.wmsback.inventory.controller;

import com.project.wmsback.inventory.dto.LotAttrChngRequest;
import com.project.wmsback.inventory.dto.LotAttrChngResponse;
import com.project.wmsback.inventory.dto.LotAttrChngSearchCond;
import com.project.wmsback.inventory.dto.LotAttrTargetResponse;
import com.project.wmsback.inventory.dto.LotAttrTargetSearchCond;
import com.project.wmsback.inventory.service.LotAttrChngService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 재고 속성변경 (Lot 속성 정정). 관리 대상은 「어떤 Lot의 속성」이라 자원 키가 lotId다.
 * 정정은 재고를 건드리지 않으므로 여기 어디에도 수량이 오가지 않는다.
 */
@RestController
@RequestMapping("/inventory/lot-attrs")
@RequiredArgsConstructor
public class LotAttrChngController {

    private final LotAttrChngService lotAttrChngService;

    /** 정정 대상 Lot 조회 (유통기한 관리 상품의 Lot만 + 영향 범위) */
    @GetMapping
    public List<LotAttrTargetResponse> listTargets(@ModelAttribute LotAttrTargetSearchCond cond) {
        return lotAttrChngService.listTargets(cond);
    }

    /** Lot 속성 정정 (제조일자·유통기한). 두 날짜는 정정 후의 최종 값이다 */
    @PutMapping("/{lotId}")
    public void change(@PathVariable Long lotId, @RequestBody LotAttrChngRequest request) {
        lotAttrChngService.change(lotId, request);
    }

    /** 정정 이력 조회 (append-only 로그 — 되돌리는 정정도 새 행이다) */
    @GetMapping("/chngs")
    public List<LotAttrChngResponse> listChngs(@ModelAttribute LotAttrChngSearchCond cond) {
        return lotAttrChngService.listChngs(cond);
    }
}
