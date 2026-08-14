package com.project.mdm.store.controller;

import com.project.mdm.store.dto.StoreResponse;
import com.project.mdm.store.dto.StoreSaveRequest;
import com.project.mdm.store.dto.StoreSearchCond;
import com.project.mdm.store.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/master/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    /** 점포 목록. 빈 조건이면 전체 — 출고주문 화면의 납품처 선택 팝업도 이 경로를 쓴다 */
    @GetMapping
    public List<StoreResponse> list(@ModelAttribute StoreSearchCond cond) {
        return storeService.list(cond);
    }

    @PostMapping("/bulk")
    public void saveAll(@RequestBody List<StoreSaveRequest> rows) {
        storeService.saveAll(rows);
    }
}
