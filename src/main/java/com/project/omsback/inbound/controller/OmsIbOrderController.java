package com.project.omsback.inbound.controller;

import com.project.omsback.inbound.dto.OmsIbLineResponse;
import com.project.omsback.inbound.dto.OmsIbOrderCreateRequest;
import com.project.omsback.inbound.dto.OmsIbOrderResponse;
import com.project.omsback.inbound.dto.OmsIbOrderSearchCond;
import com.project.omsback.inbound.service.OmsIbOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/oms/inbound-orders")
@RequiredArgsConstructor
public class OmsIbOrderController {

    private final OmsIbOrderService omsIbOrderService;

    @GetMapping
    public List<OmsIbOrderResponse> list(@ModelAttribute OmsIbOrderSearchCond cond) {
        return omsIbOrderService.list(cond);
    }

    @PostMapping
    public Long create(@RequestBody OmsIbOrderCreateRequest req) {
        return omsIbOrderService.create(req);
    }

    @GetMapping("/{omsIbOrderId}/lines")
    public List<OmsIbLineResponse> lines(@PathVariable Long omsIbOrderId) {
        return omsIbOrderService.lines(omsIbOrderId);
    }

    /** 변환 → WMS 입고예정(ASN) 생성. 반환값은 생성된 ASN의 ib_order_id */
    @PostMapping("/{omsIbOrderId}/convert")
    public Long convert(@PathVariable Long omsIbOrderId) {
        return omsIbOrderService.convert(omsIbOrderId);
    }

    /** 변환취소 → ASN을 취소하고 주문을 작성 상태로 원복 (재변환 가능) */
    @PostMapping("/{omsIbOrderId}/convert-cancel")
    public void cancelConvert(@PathVariable Long omsIbOrderId) {
        omsIbOrderService.cancelConvert(omsIbOrderId);
    }

    @PostMapping("/{omsIbOrderId}/cancel")
    public void cancel(@PathVariable Long omsIbOrderId) {
        omsIbOrderService.cancel(omsIbOrderId);
    }
}
