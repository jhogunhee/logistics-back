package com.project.wmsback.inventory.controller;

import com.project.wmsback.inventory.dto.InvAlocRecResponse;
import com.project.wmsback.inventory.dto.InvResponse;
import com.project.wmsback.inventory.dto.InvSearchCond;
import com.project.wmsback.inventory.service.InvService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/inventory/stock")
@RequiredArgsConstructor
public class InvController {

    private final InvService invService;

    @GetMapping
    public List<InvResponse> list(@ModelAttribute InvSearchCond cond) {
        return invService.list(cond);
    }

    /**
     * 예약 대사 — aloc_qty의 원장이 없어(물리 이동이 아니라 이력에 안 남긴다) 원천별 미소진 잔량을 다시
     * 합산해 장부와 견준다. 할당해제·지시취소·결품 종결·출고확정이 예약을 제대로 돌려놓았는지의 유일한 검증 수단.
     */
    @GetMapping("/aloc-reconciliation")
    public List<InvAlocRecResponse> alocReconciliation() {
        return invService.reconcileAloc();
    }
}
