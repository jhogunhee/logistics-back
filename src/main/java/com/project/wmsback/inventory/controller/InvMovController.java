package com.project.wmsback.inventory.controller;

import com.project.wmsback.inventory.dto.InvMovConfirmRequest;
import com.project.wmsback.inventory.dto.InvMovRegisterRequest;
import com.project.wmsback.inventory.dto.InvMovTaskResponse;
import com.project.wmsback.inventory.dto.InvMovTaskSearchCond;
import com.project.wmsback.inventory.service.InvMovService;
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
@RequestMapping("/inventory/moves")
@RequiredArgsConstructor
public class InvMovController {

    private final InvMovService invMovService;

    /** 이동지시 등록 (예약). 발급된 이동지시 번호 목록을 돌려준다 */
    @PostMapping
    public List<String> register(@RequestBody InvMovRegisterRequest request) {
        return invMovService.register(request);
    }

    @GetMapping
    public List<InvMovTaskResponse> list(@ModelAttribute InvMovTaskSearchCond cond) {
        return invMovService.list(cond);
    }

    /** 이동확정 (지시를 지목해 다건, 건마다 부분확정 허용) */
    @PostMapping("/confirm")
    public void confirm(@RequestBody InvMovConfirmRequest request) {
        invMovService.confirm(request);
    }

    /** 이동취소 (잔량 취소 — 예약 해제) */
    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable Long id) {
        invMovService.cancel(id);
    }
}
