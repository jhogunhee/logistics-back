package com.project.omsback.outbound.controller;

import com.project.omsback.outbound.dto.OmsOutbLineResponse;
import com.project.omsback.outbound.dto.OmsOutbOrderResponse;
import com.project.omsback.outbound.dto.OmsOutbOrderSaveRequest;
import com.project.omsback.outbound.dto.OmsOutbOrderSearchCond;
import com.project.omsback.outbound.service.OmsOutbOrderService;
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
@RequestMapping("/oms/outbound-orders")
@RequiredArgsConstructor
public class OmsOutbOrderController {

    private final OmsOutbOrderService omsOutbOrderService;

    @GetMapping
    public List<OmsOutbOrderResponse> list(@ModelAttribute OmsOutbOrderSearchCond cond) {
        return omsOutbOrderService.list(cond);
    }

    @PostMapping
    public Long create(@RequestBody OmsOutbOrderSaveRequest req) {
        return omsOutbOrderService.create(req);
    }

    /** 수정. 작성(CREATED) 상태만 가능 — 확정된 주문은 확정취소가 먼저다 (판정은 엔티티) */
    @PutMapping("/{omsOutbOrderId}")
    public void update(@PathVariable Long omsOutbOrderId, @RequestBody OmsOutbOrderSaveRequest req) {
        omsOutbOrderService.update(omsOutbOrderId, req);
    }

    @GetMapping("/{omsOutbOrderId}/lines")
    public List<OmsOutbLineResponse> lines(@PathVariable Long omsOutbOrderId) {
        return omsOutbOrderService.lines(omsOutbOrderId);
    }

    /** 확정 → WMS 출고주문 생성. 반환값은 생성된 outb_order_id */
    @PostMapping("/{omsOutbOrderId}/confirm")
    public Long confirm(@PathVariable Long omsOutbOrderId) {
        return omsOutbOrderService.confirm(omsOutbOrderId);
    }

    /** 확정취소 → WMS 출고주문을 삭제하고 주문을 작성 상태로 원복 (재확정 가능) */
    @PostMapping("/{omsOutbOrderId}/confirm-cancel")
    public void cancelConfirm(@PathVariable Long omsOutbOrderId) {
        omsOutbOrderService.cancelConfirm(omsOutbOrderId);
    }

    @DeleteMapping("/{omsOutbOrderId}")
    public void delete(@PathVariable Long omsOutbOrderId) {
        omsOutbOrderService.delete(omsOutbOrderId);
    }
}
