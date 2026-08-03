package com.project.mdm.store.controller;

import com.project.mdm.store.dto.StoreResponse;
import com.project.mdm.store.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/master/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    /** 점포(납품처) 전체 목록. 출고주문 화면의 납품처 선택 팝업이 쓴다 */
    @GetMapping
    public List<StoreResponse> list() {
        return storeService.list();
    }
}
