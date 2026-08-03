package com.project.wmsback.warehouse.controller;

import com.project.wmsback.warehouse.dto.LotResponse;
import com.project.wmsback.warehouse.service.LotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/master/lots")
@RequiredArgsConstructor
public class LotController {

    private final LotService lotService;

    /** 상품별 Lot 목록 (조회 전용). 재고조사의 라인 수동 추가에서 Lot을 고를 때 쓴다 */
    @GetMapping
    public List<LotResponse> list(@RequestParam Long prodId) {
        return lotService.listByProd(prodId);
    }
}
