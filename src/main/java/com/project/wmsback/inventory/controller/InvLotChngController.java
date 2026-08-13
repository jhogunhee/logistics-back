package com.project.wmsback.inventory.controller;

import com.project.wmsback.inventory.dto.InvLotChngRequest;
import com.project.wmsback.inventory.dto.InvLotChngResponse;
import com.project.wmsback.inventory.dto.InvLotChngSearchCond;
import com.project.wmsback.inventory.dto.InvLotChngTargetResponse;
import com.project.wmsback.inventory.dto.InvLotChngTargetSearchCond;
import com.project.wmsback.inventory.service.InvLotChngService;
import com.project.wmsback.warehouse.dto.LotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 재고 로트변경 — 수량을 지정한 Lot 속성정정 (재고를 움직이지 않는 재고 속성변경과 별개 조작).
 * 원 Lot에서 N개를 빼 (상품+입고일자+정정 제조일자) 배치의 Lot으로 넣는다.
 */
@RestController
@RequestMapping("/inventory/lot-chngs")
@RequiredArgsConstructor
public class InvLotChngController {

    private final InvLotChngService invLotChngService;

    /** 변경 대상 재고 행 조회 — 보관 로케이션 + 유통기한 관리 상품 + 가용수량 > 0 (서버 강제) */
    @GetMapping("/targets")
    public List<InvLotChngTargetResponse> listTargets(@ModelAttribute InvLotChngTargetSearchCond cond) {
        return invLotChngService.listTargets(cond);
    }

    /** 목적지 배치 후보 조회 — 원 Lot과 같은 상품+입고일자인 다른 Lot들 (고르면 병합, 없으면 직접 입력=분할) */
    @GetMapping("/target-lots")
    public List<LotResponse> listTargetLots(@RequestParam Long invId) {
        return invLotChngService.listTargetLots(invId);
    }

    /** 로트변경 실행. 발급된 로트변경 번호 목록을 돌려준다 (요청 순서) */
    @PostMapping
    public List<String> change(@RequestBody InvLotChngRequest request) {
        return invLotChngService.change(request);
    }

    /** 실적 조회 (append-only 로그) */
    @GetMapping
    public List<InvLotChngResponse> list(@ModelAttribute InvLotChngSearchCond cond) {
        return invLotChngService.list(cond);
    }
}
