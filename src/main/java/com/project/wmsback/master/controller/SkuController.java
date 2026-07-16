package com.project.wmsback.master.controller;

import com.project.wmsback.master.dto.SkuResponse;
import com.project.wmsback.master.dto.SkuSaveRequest;
import com.project.wmsback.master.dto.SkuSearchCond;
import com.project.wmsback.master.service.SkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/master/skus")
@RequiredArgsConstructor
public class SkuController {

    private final SkuService skuService;

    @GetMapping
    public List<SkuResponse> list(@ModelAttribute SkuSearchCond cond) {
        return skuService.list(cond);
    }

    @PostMapping("/bulk")
    public void saveAll(@RequestBody List<SkuSaveRequest> rows) {
        skuService.saveAll(rows);
    }
}