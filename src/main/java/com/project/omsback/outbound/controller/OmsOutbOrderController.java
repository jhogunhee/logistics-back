package com.project.omsback.outbound.controller;

import com.project.omsback.outbound.dto.OmsOutbLineResponse;
import com.project.omsback.outbound.dto.OmsOutbOrderResponse;
import com.project.omsback.outbound.dto.OmsOutbOrderSaveRequest;
import com.project.omsback.outbound.dto.OmsOutbOrderSearchCond;
import com.project.omsback.outbound.service.OmsOutbOrderService;
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
@RequestMapping("/oms/outbound-orders")
@RequiredArgsConstructor
public class OmsOutbOrderController {

    private final OmsOutbOrderService omsOutbOrderService;

    @GetMapping
    public List<OmsOutbOrderResponse> list(@ModelAttribute OmsOutbOrderSearchCond cond) {
        return omsOutbOrderService.list(cond);
    }

    @GetMapping("/{omsOutbOrderId}")
    public OmsOutbOrderResponse get(@PathVariable Long omsOutbOrderId) {
        return omsOutbOrderService.get(omsOutbOrderId);
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

    /**
     * 일괄 확정 → 건마다 WMS 출고주문 생성.
     * 화면의 체크 목록을 한 요청으로 받는다 — 건별 성공/실패는 응답(BatchResult)에서 갈린다.
     * body: id 배열
     */
    @PostMapping("/confirm")
    public BatchResult confirm(@RequestBody List<Long> omsOutbOrderIds) {
        return omsOutbOrderService.confirmAll(omsOutbOrderIds);
    }

    /** 일괄 확정취소 → WMS 출고주문을 삭제하고 주문을 작성 상태로 원복 (재확정 가능). body: id 배열 */
    @PostMapping("/confirm-cancel")
    public BatchResult cancelConfirm(@RequestBody List<Long> omsOutbOrderIds) {
        return omsOutbOrderService.cancelConfirmAll(omsOutbOrderIds);
    }

    /** 일괄 삭제. 작성 상태만 — DELETE는 본문 없이 ?ids=1,2,3 으로 받는다 */
    @DeleteMapping
    public BatchResult delete(@RequestParam("ids") List<Long> omsOutbOrderIds) {
        return omsOutbOrderService.deleteAll(omsOutbOrderIds);
    }
}
