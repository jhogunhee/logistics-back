package com.project.omsback.inbound.controller;

import com.project.omsback.inbound.dto.OmsIbLineResponse;
import com.project.omsback.inbound.dto.OmsIbOrderSaveRequest;
import com.project.omsback.inbound.dto.OmsIbOrderResponse;
import com.project.omsback.inbound.dto.OmsIbOrderSearchCond;
import com.project.omsback.inbound.service.OmsIbOrderService;
import com.project.common.batch.BatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/{omsIbOrderId}")
    public OmsIbOrderResponse get(@PathVariable Long omsIbOrderId) {
        return omsIbOrderService.get(omsIbOrderId);
    }

    @PostMapping
    public Long create(@RequestBody OmsIbOrderSaveRequest req) {
        return omsIbOrderService.create(req);
    }

    /** 수정. 작성(CREATED) 상태만 가능 — 확정된 주문은 확정취소가 먼저다 (판정은 엔티티) */
    @PutMapping("/{omsIbOrderId}")
    public void update(@PathVariable Long omsIbOrderId, @RequestBody OmsIbOrderSaveRequest req) {
        omsIbOrderService.update(omsIbOrderId, req);
    }

    @GetMapping("/{omsIbOrderId}/lines")
    public List<OmsIbLineResponse> lines(@PathVariable Long omsIbOrderId) {
        return omsIbOrderService.lines(omsIbOrderId);
    }

    /**
     * 일괄 확정 → 건마다 WMS 입고예정(ASN) 생성.
     * 화면의 체크 목록을 한 요청으로 받는다 — 건별 성공/실패는 응답(BatchResult)에서 갈린다.
     * body: id 배열
     */
    @PostMapping("/confirm")
    public BatchResult confirm(@RequestBody List<Long> omsIbOrderIds) {
        return omsIbOrderService.confirmAll(omsIbOrderIds);
    }

    /** 일괄 확정취소 → ASN을 취소하고 주문을 작성 상태로 원복 (재확정 가능). body: id 배열 */
    @PostMapping("/confirm-cancel")
    public BatchResult cancelConfirm(@RequestBody List<Long> omsIbOrderIds) {
        return omsIbOrderService.cancelConfirmAll(omsIbOrderIds);
    }

    /** 일괄 삭제. 작성 상태만 — DELETE는 본문 없이 ?ids=1,2,3 으로 받는다 */
    @DeleteMapping
    public BatchResult delete(@RequestParam("ids") List<Long> omsIbOrderIds) {
        return omsIbOrderService.deleteAll(omsIbOrderIds);
    }
}
