package com.project.wmsback.master.controller;

import com.project.wmsback.master.dto.ProdUomResponse;
import com.project.wmsback.master.dto.ProdUomSaveRequest;
import com.project.wmsback.master.dto.ProdUomSearchCond;
import com.project.wmsback.master.service.ProdUomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 단위(상품 포장) 관리 화면 */
@RestController
@RequestMapping("/master/prod-uoms")
@RequiredArgsConstructor
public class ProdUomController {

    private final ProdUomService prodUomService;

    @GetMapping
    public List<ProdUomResponse> list(@ModelAttribute ProdUomSearchCond cond) {
        return prodUomService.list(cond);
    }

    @PostMapping("/bulk")
    public void saveAll(@RequestBody List<ProdUomSaveRequest> rows) {
        prodUomService.saveAll(rows);
    }
}
